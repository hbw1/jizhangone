from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.models.entities import Account, Book, BookMembership, Category, Member, TransactionType, User
from app.schemas.nlp import CloudParseRequest, CloudParseResponse, CloudParsedTransactionCandidate

settings = get_settings()


class NlpPermissionError(Exception):
    pass


class NlpProviderConfigurationError(Exception):
    pass


class NlpProviderResponseError(Exception):
    pass


@dataclass(slots=True)
class ParseContext:
    book_name: str
    member_names: list[str]
    account_names: list[str]
    expense_category_names: list[str]
    income_category_names: list[str]


async def parse_natural_language_with_minimax(
    db: AsyncSession,
    current_user: User,
    payload: CloudParseRequest,
) -> CloudParseResponse:
    if not settings.minimax_api_key:
        raise NlpProviderConfigurationError("MiniMax API key is not configured")

    context = await load_parse_context(db, current_user.id, payload.book_id)
    provider_payload = build_provider_payload(payload, context)
    response_json = await call_minimax(provider_payload)
    parsed = normalize_provider_response(
        raw_text=payload.raw_text,
        model=response_json.get("model") or settings.minimax_model,
        content=extract_message_content(response_json),
        context=context,
    )
    return parsed


async def load_parse_context(db: AsyncSession, user_id: str, book_id: str) -> ParseContext:
    membership_result = await db.execute(
        select(BookMembership)
        .where(BookMembership.user_id == user_id, BookMembership.book_id == book_id),
    )
    membership = membership_result.scalar_one_or_none()
    if membership is None:
        raise NlpPermissionError("You do not have access to this book")

    book_result = await db.execute(select(Book).where(Book.id == book_id, Book.deleted_at.is_(None)))
    book = book_result.scalar_one_or_none()
    if book is None:
        raise NlpPermissionError("Book not found")

    members_result = await db.execute(
        select(Member.name)
        .where(Member.book_id == book_id, Member.deleted_at.is_(None), Member.active.is_(True))
        .order_by(Member.created_at.asc()),
    )
    accounts_result = await db.execute(
        select(Account.name)
        .where(Account.book_id == book_id, Account.deleted_at.is_(None), Account.active.is_(True))
        .order_by(Account.created_at.asc()),
    )
    expense_categories_result = await db.execute(
        select(Category.name)
        .where(
            Category.book_id == book_id,
            Category.deleted_at.is_(None),
            Category.active.is_(True),
            Category.type == TransactionType.EXPENSE,
        )
        .order_by(Category.sort_order.asc(), Category.created_at.asc()),
    )
    income_categories_result = await db.execute(
        select(Category.name)
        .where(
            Category.book_id == book_id,
            Category.deleted_at.is_(None),
            Category.active.is_(True),
            Category.type == TransactionType.INCOME,
        )
        .order_by(Category.sort_order.asc(), Category.created_at.asc()),
    )

    return ParseContext(
        book_name=book.name,
        member_names=list(members_result.scalars().all()),
        account_names=list(accounts_result.scalars().all()),
        expense_category_names=list(expense_categories_result.scalars().all()),
        income_category_names=list(income_categories_result.scalars().all()),
    )


def build_provider_payload(payload: CloudParseRequest, context: ParseContext) -> dict:
    today = payload.today or datetime.now(UTC).date()
    system_prompt = """
You are a household ledger parser.
Return valid JSON only.
Do not add markdown fences.
Use this exact shape:
{
  "diary_text": "string",
  "candidates": [
    {
      "type": "expense" | "income",
      "amount": 0,
      "occurred_on": "YYYY-MM-DD" | null,
      "member_name": "string | null",
      "category_name": "string | null",
      "note": "string",
      "confidence": 0.0,
      "source_snippet": "string"
    }
  ],
  "warnings": ["string"]
}
Rules:
- diary_text should preserve the original meaning of the full input.
- candidates should contain one item per transaction-like event.
- amount is required for a valid candidate. If no amount is present, omit the candidate.
- occurred_on must be inferred from relative time expressions using the provided today value.
- member_name must prefer the provided member list. If no confident match exists, use null.
- category_name must prefer the provided category lists. If no confident match exists, use null.
- confidence must be between 0 and 1.
- source_snippet should be the most relevant phrase for that candidate.
""".strip()

    user_prompt = {
        "today": today.isoformat(),
        "timezone": payload.timezone,
        "book_name": context.book_name,
        "members": context.member_names,
        "accounts": context.account_names,
        "expense_categories": context.expense_category_names,
        "income_categories": context.income_category_names,
        "raw_text": payload.raw_text,
    }

    return {
        "model": settings.minimax_model,
        "temperature": 0.1,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": json.dumps(user_prompt, ensure_ascii=False)},
        ],
    }


async def call_minimax(provider_payload: dict) -> dict:
    endpoint = f"{settings.minimax_base_url.rstrip('/')}/chat/completions"
    headers = {
        "Authorization": f"Bearer {settings.minimax_api_key}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=settings.minimax_timeout_seconds) as client:
            response = await client.post(endpoint, headers=headers, json=provider_payload)
    except httpx.HTTPError as exc:
        raise NlpProviderResponseError(f"MiniMax request failed: {exc}") from exc

    if response.status_code >= 400:
        raise NlpProviderResponseError(
            f"MiniMax returned HTTP {response.status_code}: {response.text[:300]}",
        )

    try:
        return response.json()
    except json.JSONDecodeError as exc:
        raise NlpProviderResponseError("MiniMax returned non-JSON response") from exc


def extract_message_content(provider_response: dict) -> str:
    try:
        content = provider_response["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise NlpProviderResponseError("MiniMax response missing choices[0].message.content") from exc

    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, dict) and item.get("type") == "text":
                parts.append(str(item.get("text", "")))
        return "".join(parts)
    raise NlpProviderResponseError("MiniMax message content format is unsupported")


def normalize_provider_response(
    raw_text: str,
    model: str,
    content: str,
    context: ParseContext,
) -> CloudParseResponse:
    payload = parse_json_object(content)
    warnings = list(payload.get("warnings") or [])
    diary_text = str(payload.get("diary_text") or raw_text)

    candidates: list[CloudParsedTransactionCandidate] = []
    for item in payload.get("candidates") or []:
        if not isinstance(item, dict):
            continue

        candidate_type = normalize_type(item.get("type"))
        amount = normalize_amount(item.get("amount"))
        if candidate_type is None or amount is None:
            continue

        member_name = normalize_name(item.get("member_name"), context.member_names)
        category_names = (
            context.income_category_names
            if candidate_type == TransactionType.INCOME
            else context.expense_category_names
        )
        category_name = normalize_name(item.get("category_name"), category_names)

        if item.get("member_name") and member_name is None:
            warnings.append(f"未匹配成员：{item.get('member_name')}")
        if item.get("category_name") and category_name is None:
            warnings.append(f"未匹配分类：{item.get('category_name')}")

        candidates.append(
            CloudParsedTransactionCandidate(
                type=candidate_type,
                amount=amount,
                occurred_on=normalize_date(item.get("occurred_on")),
                member_name=member_name,
                category_name=category_name,
                note=str(item.get("note") or "").strip(),
                confidence=normalize_confidence(item.get("confidence")),
                source_snippet=str(item.get("source_snippet") or "").strip(),
            ),
        )

    if not candidates:
        warnings.append("当前没有识别出可入账的金额片段。")

    return CloudParseResponse(
        model=model,
        raw_text=raw_text,
        diary_text=diary_text,
        candidates=candidates,
        warnings=dedupe_preserve_order(warnings),
    )


def parse_json_object(content: str) -> dict:
    normalized = content.strip()
    if normalized.startswith("```"):
        normalized = normalized.strip("`")
        normalized = normalized.replace("json", "", 1).strip()

    if not normalized.startswith("{"):
        start = normalized.find("{")
        end = normalized.rfind("}")
        if start >= 0 and end > start:
            normalized = normalized[start : end + 1]

    try:
        parsed = json.loads(normalized)
    except json.JSONDecodeError as exc:
        raise NlpProviderResponseError("MiniMax content was not valid JSON") from exc

    if not isinstance(parsed, dict):
        raise NlpProviderResponseError("MiniMax JSON payload must be an object")
    return parsed


def normalize_type(value: object) -> TransactionType | None:
    if not isinstance(value, str):
        return None
    lowered = value.strip().lower()
    if lowered == "expense":
        return TransactionType.EXPENSE
    if lowered == "income":
        return TransactionType.INCOME
    return None


def normalize_amount(value: object) -> float | None:
    if value is None:
        return None
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


def normalize_date(value: object):
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        return datetime.fromisoformat(f"{value}T00:00:00+00:00").date()
    except ValueError:
        return None


def normalize_name(value: object, allowed: list[str]) -> str | None:
    if not isinstance(value, str):
        return None
    candidate = value.strip()
    if not candidate:
        return None

    exact = next((item for item in allowed if item == candidate), None)
    if exact:
        return exact

    lowered = candidate.casefold()
    return next((item for item in allowed if item.casefold() == lowered), None)


def normalize_confidence(value: object) -> float:
    try:
        return max(0.0, min(float(value), 1.0))
    except (TypeError, ValueError):
        return 0.0


def dedupe_preserve_order(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        normalized = value.strip()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
    return result

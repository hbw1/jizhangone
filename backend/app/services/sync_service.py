from __future__ import annotations

import json
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import Select, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.entities import (
    Account,
    Book,
    BookMembership,
    Category,
    JournalEntry,
    Member,
    OperationLog,
    SourceType,
    Transaction,
    TransactionType,
    User,
)
from app.schemas.sync import SyncChange, SyncOperationAck, SyncPullResponse, SyncPushRequest, SyncPushResponse


class SyncPermissionError(ValueError):
    """Raised when a user cannot access a book."""


class SyncValidationError(ValueError):
    """Raised when a sync operation payload is invalid."""


async def push_changes(
    db: AsyncSession,
    user: User,
    payload: SyncPushRequest,
) -> SyncPushResponse:
    await ensure_book_access(db, user.id, payload.book_id)
    applied: list[SyncOperationAck] = []
    latest_sequence = 0

    for operation in payload.operations:
        duplicate = await db.execute(
            select(OperationLog).where(OperationLog.client_mutation_id == operation.client_mutation_id)
        )
        existing = duplicate.scalar_one_or_none()
        if existing is not None:
            applied.append(
                SyncOperationAck(
                    sequence=existing.sequence,
                    entity_type=existing.entity_type,
                    operation_type=existing.operation_type,
                    entity_id=existing.entity_id,
                    client_mutation_id=existing.client_mutation_id,
                    status="duplicate",
                )
            )
            latest_sequence = max(latest_sequence, existing.sequence)
            continue

        log = await apply_operation(
            db=db,
            user=user,
            book_id=payload.book_id,
            entity_type=operation.entity_type,
            operation_type=operation.operation_type,
            data=operation.payload,
            client_mutation_id=operation.client_mutation_id,
        )
        applied.append(
            SyncOperationAck(
                sequence=log.sequence,
                entity_type=log.entity_type,
                operation_type=log.operation_type,
                entity_id=log.entity_id,
                client_mutation_id=log.client_mutation_id,
                status="applied",
            )
        )
        latest_sequence = max(latest_sequence, log.sequence)

    await db.commit()

    if latest_sequence == 0:
        last_log = await db.execute(
            select(OperationLog)
            .where(OperationLog.book_id == payload.book_id)
            .order_by(OperationLog.sequence.desc())
            .limit(1)
        )
        latest = last_log.scalar_one_or_none()
        latest_sequence = latest.sequence if latest is not None else 0

    return SyncPushResponse(
        book_id=payload.book_id,
        applied=applied,
        next_cursor=str(latest_sequence),
    )


async def pull_changes(
    db: AsyncSession,
    user: User,
    book_id: str,
    cursor: str | None,
) -> SyncPullResponse:
    await ensure_book_access(db, user.id, book_id)
    sequence_cursor = parse_cursor(cursor)

    stmt: Select[tuple[OperationLog]] = (
        select(OperationLog)
        .where(OperationLog.book_id == book_id)
        .order_by(OperationLog.sequence.asc())
    )
    if sequence_cursor is not None:
        stmt = stmt.where(OperationLog.sequence > sequence_cursor)

    logs = (await db.execute(stmt)).scalars().all()
    changes = [
        SyncChange(
            sequence=log.sequence,
            entity_type=log.entity_type,
            operation_type=log.operation_type,
            entity_id=log.entity_id,
            client_mutation_id=log.client_mutation_id,
            created_at=log.created_at,
            payload=json.loads(log.payload_json),
        )
        for log in logs
    ]
    next_cursor = str(logs[-1].sequence) if logs else str(sequence_cursor or 0)
    return SyncPullResponse(
        book_id=book_id,
        changes=changes,
        next_cursor=next_cursor,
    )


async def log_entity_change(
    db: AsyncSession,
    *,
    book_id: str,
    entity_type: str,
    operation_type: str,
    entity_id: str,
    payload: dict[str, Any],
    client_mutation_id: str,
    operator_user_id: str,
) -> OperationLog:
    log = OperationLog(
        book_id=book_id,
        entity_type=entity_type,
        entity_id=entity_id,
        operation_type=operation_type,
        payload_json=json.dumps(payload, ensure_ascii=False),
        client_mutation_id=client_mutation_id,
        operator_user_id=operator_user_id,
    )
    db.add(log)
    await db.flush()
    return log


async def apply_operation(
    *,
    db: AsyncSession,
    user: User,
    book_id: str,
    entity_type: str,
    operation_type: str,
    data: dict[str, Any],
    client_mutation_id: str,
) -> OperationLog:
    if operation_type == "delete":
        return await apply_delete_operation(
            db=db,
            user=user,
            book_id=book_id,
            entity_type=entity_type,
            data=data,
            client_mutation_id=client_mutation_id,
        )
    if operation_type != "upsert":
        raise SyncValidationError("Unsupported operation type")

    if entity_type == "book":
        entity = await upsert_book(db, book_id, data)
    elif entity_type == "member":
        entity = await upsert_member(db, book_id, data)
    elif entity_type == "account":
        entity = await upsert_account(db, book_id, data)
    elif entity_type == "category":
        entity = await upsert_category(db, book_id, data)
    elif entity_type == "journal_entry":
        entity = await upsert_journal_entry(db, book_id, data)
    elif entity_type == "transaction":
        entity = await upsert_transaction(db, user.id, book_id, data)
    else:
        raise SyncValidationError("Unsupported entity type")

    serialized = serialize_entity(entity_type, entity)
    return await log_entity_change(
        db,
        book_id=book_id,
        entity_type=entity_type,
        operation_type="upsert",
        entity_id=serialized["id"],
        payload=serialized,
        client_mutation_id=client_mutation_id,
        operator_user_id=user.id,
    )


async def apply_delete_operation(
    *,
    db: AsyncSession,
    user: User,
    book_id: str,
    entity_type: str,
    data: dict[str, Any],
    client_mutation_id: str,
) -> OperationLog:
    entity_id = required_str(data, "id")
    entity = await load_entity_for_book(db, entity_type, book_id, entity_id)
    if entity is None:
        raise SyncValidationError(f"{entity_type} not found")

    entity.deleted_at = datetime.now(UTC)
    if hasattr(entity, "updated_at"):
        entity.updated_at = datetime.now(UTC)
    await db.flush()

    payload = {
        "id": entity_id,
        "deleted_at": to_iso(entity.deleted_at),
    }
    return await log_entity_change(
        db,
        book_id=book_id,
        entity_type=entity_type,
        operation_type="delete",
        entity_id=entity_id,
        payload=payload,
        client_mutation_id=client_mutation_id,
        operator_user_id=user.id,
    )


async def ensure_book_access(db: AsyncSession, user_id: str, book_id: str) -> None:
    stmt = select(BookMembership).where(
        BookMembership.book_id == book_id,
        BookMembership.user_id == user_id,
    )
    membership = (await db.execute(stmt)).scalar_one_or_none()
    if membership is None:
        raise SyncPermissionError("No permission for this book")


async def upsert_book(db: AsyncSession, book_id: str, data: dict[str, Any]) -> Book:
    stmt = select(Book).where(Book.id == book_id, Book.deleted_at.is_(None))
    book = (await db.execute(stmt)).scalar_one_or_none()
    if book is None:
        raise SyncValidationError("Book not found")

    if "name" in data:
        book.name = required_str(data, "name")
    book.updated_at = datetime.now(UTC)
    await db.flush()
    return book


async def upsert_member(db: AsyncSession, book_id: str, data: dict[str, Any]) -> Member:
    entity_id = optional_str(data, "id")
    member = await maybe_load_entity(db, Member, book_id, entity_id)
    if member is None:
        member_kwargs = {"book_id": book_id, "name": required_str(data, "name")}
        if entity_id:
            member_kwargs["id"] = entity_id
        member = Member(**member_kwargs)
        db.add(member)
    else:
        member.name = required_str(data, "name")
    if "active" in data:
        member.active = bool(data["active"])
    member.deleted_at = None
    member.updated_at = datetime.now(UTC)
    await db.flush()
    return member


async def upsert_account(db: AsyncSession, book_id: str, data: dict[str, Any]) -> Account:
    entity_id = optional_str(data, "id")
    account = await maybe_load_entity(db, Account, book_id, entity_id)
    if account is None:
        account_kwargs = {
            "book_id": book_id,
            "name": required_str(data, "name"),
            "initial_balance": required_float(data, "initial_balance", default=0.0),
        }
        if entity_id:
            account_kwargs["id"] = entity_id
        account = Account(**account_kwargs)
        db.add(account)
    else:
        account.name = required_str(data, "name")
        if "initial_balance" in data:
            account.initial_balance = required_float(data, "initial_balance")
    if "active" in data:
        account.active = bool(data["active"])
    account.deleted_at = None
    account.updated_at = datetime.now(UTC)
    await db.flush()
    return account


async def upsert_category(db: AsyncSession, book_id: str, data: dict[str, Any]) -> Category:
    entity_id = optional_str(data, "id")
    category = await maybe_load_entity(db, Category, book_id, entity_id)
    category_type = parse_transaction_type(required_str(data, "type"))
    if category is None:
        category_kwargs = {
            "book_id": book_id,
            "type": category_type,
            "name": required_str(data, "name"),
            "sort_order": required_int(data, "sort_order", default=0),
        }
        if entity_id:
            category_kwargs["id"] = entity_id
        category = Category(**category_kwargs)
        db.add(category)
    else:
        category.type = category_type
        category.name = required_str(data, "name")
        if "sort_order" in data:
            category.sort_order = required_int(data, "sort_order")
    if "active" in data:
        category.active = bool(data["active"])
    category.deleted_at = None
    category.updated_at = datetime.now(UTC)
    await db.flush()
    return category


async def upsert_journal_entry(db: AsyncSession, book_id: str, data: dict[str, Any]) -> JournalEntry:
    entity_id = optional_str(data, "id")
    journal = await maybe_load_entity(db, JournalEntry, book_id, entity_id)
    if journal is None:
        journal_kwargs = {
            "book_id": book_id,
            "entry_date": parse_datetime(required_str(data, "entry_date")),
            "raw_text": required_str(data, "raw_text"),
            "source_type": parse_source_type(optional_str(data, "source_type") or "manual"),
        }
        if entity_id:
            journal_kwargs["id"] = entity_id
        journal = JournalEntry(**journal_kwargs)
        db.add(journal)
    else:
        journal.entry_date = parse_datetime(required_str(data, "entry_date"))
        journal.raw_text = required_str(data, "raw_text")
        if "source_type" in data:
            journal.source_type = parse_source_type(required_str(data, "source_type"))
    journal.deleted_at = None
    journal.updated_at = datetime.now(UTC)
    await db.flush()
    return journal


async def upsert_transaction(
    db: AsyncSession,
    user_id: str,
    book_id: str,
    data: dict[str, Any],
) -> Transaction:
    entity_id = optional_str(data, "id")
    transaction = await maybe_load_entity(db, Transaction, book_id, entity_id)
    values = {
        "member_id": required_str(data, "member_id"),
        "account_id": required_str(data, "account_id"),
        "category_id": required_str(data, "category_id"),
        "journal_entry_id": optional_str(data, "journal_entry_id"),
        "type": parse_transaction_type(required_str(data, "type")),
        "amount": required_float(data, "amount"),
        "occurred_at": parse_datetime(required_str(data, "occurred_at")),
        "note": optional_str(data, "note") or "",
        "source_type": parse_source_type(optional_str(data, "source_type") or "manual"),
    }

    if transaction is None:
        transaction_kwargs = {
            "book_id": book_id,
            "created_by_user_id": user_id,
            "version": 1,
            **values,
        }
        if entity_id:
            transaction_kwargs["id"] = entity_id
        transaction = Transaction(**transaction_kwargs)
        db.add(transaction)
    else:
        for key, value in values.items():
            setattr(transaction, key, value)
        transaction.version += 1

    transaction.deleted_at = None
    transaction.updated_at = datetime.now(UTC)
    await db.flush()
    return transaction


async def maybe_load_entity(
    db: AsyncSession,
    model: type[Any],
    book_id: str,
    entity_id: str | None,
) -> Any | None:
    if not entity_id:
        return None
    return await load_entity_for_book(db, entity_type_for_model(model), book_id, entity_id)


async def load_entity_for_book(
    db: AsyncSession,
    entity_type: str,
    book_id: str,
    entity_id: str,
) -> Any | None:
    model = model_for_entity_type(entity_type)
    if entity_type == "book":
        stmt = select(model).where(model.id == entity_id)
    else:
        stmt = select(model).where(model.id == entity_id, model.book_id == book_id)
    return (await db.execute(stmt)).scalar_one_or_none()


def model_for_entity_type(entity_type: str) -> type[Any]:
    mapping: dict[str, type[Any]] = {
        "book": Book,
        "member": Member,
        "account": Account,
        "category": Category,
        "journal_entry": JournalEntry,
        "transaction": Transaction,
    }
    try:
        return mapping[entity_type]
    except KeyError as exc:
        raise SyncValidationError("Unsupported entity type") from exc


def entity_type_for_model(model: type[Any]) -> str:
    reverse = {
        Book: "book",
        Member: "member",
        Account: "account",
        Category: "category",
        JournalEntry: "journal_entry",
        Transaction: "transaction",
    }
    return reverse[model]


def serialize_entity(entity_type: str, entity: Any) -> dict[str, Any]:
    if entity_type == "book":
        return {
            "id": entity.id,
            "name": entity.name,
            "created_at": to_iso(entity.created_at),
            "updated_at": to_iso(entity.updated_at),
            "deleted_at": to_iso(entity.deleted_at),
        }
    if entity_type == "member":
        return {
            "id": entity.id,
            "book_id": entity.book_id,
            "name": entity.name,
            "active": entity.active,
            "created_at": to_iso(entity.created_at),
            "updated_at": to_iso(entity.updated_at),
            "deleted_at": to_iso(entity.deleted_at),
        }
    if entity_type == "account":
        return {
            "id": entity.id,
            "book_id": entity.book_id,
            "name": entity.name,
            "initial_balance": to_float(entity.initial_balance),
            "active": entity.active,
            "created_at": to_iso(entity.created_at),
            "updated_at": to_iso(entity.updated_at),
            "deleted_at": to_iso(entity.deleted_at),
        }
    if entity_type == "category":
        return {
            "id": entity.id,
            "book_id": entity.book_id,
            "type": entity.type.value,
            "name": entity.name,
            "sort_order": entity.sort_order,
            "active": entity.active,
            "created_at": to_iso(entity.created_at),
            "updated_at": to_iso(entity.updated_at),
            "deleted_at": to_iso(entity.deleted_at),
        }
    if entity_type == "journal_entry":
        return {
            "id": entity.id,
            "book_id": entity.book_id,
            "entry_date": to_iso(entity.entry_date),
            "raw_text": entity.raw_text,
            "source_type": entity.source_type.value,
            "created_at": to_iso(entity.created_at),
            "updated_at": to_iso(entity.updated_at),
            "deleted_at": to_iso(entity.deleted_at),
        }
    if entity_type == "transaction":
        return {
            "id": entity.id,
            "book_id": entity.book_id,
            "member_id": entity.member_id,
            "account_id": entity.account_id,
            "category_id": entity.category_id,
            "journal_entry_id": entity.journal_entry_id,
            "type": entity.type.value,
            "amount": to_float(entity.amount),
            "occurred_at": to_iso(entity.occurred_at),
            "note": entity.note,
            "source_type": entity.source_type.value,
            "created_by_user_id": entity.created_by_user_id,
            "version": entity.version,
            "created_at": to_iso(entity.created_at),
            "updated_at": to_iso(entity.updated_at),
            "deleted_at": to_iso(entity.deleted_at),
        }
    raise SyncValidationError("Unsupported entity type")


def required_str(data: dict[str, Any], key: str) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise SyncValidationError(f"Missing or invalid field: {key}")
    return value.strip()


def optional_str(data: dict[str, Any], key: str) -> str | None:
    value = data.get(key)
    if value is None:
        return None
    if not isinstance(value, str):
        raise SyncValidationError(f"Invalid field: {key}")
    stripped = value.strip()
    return stripped or None


def required_float(data: dict[str, Any], key: str, default: float | None = None) -> float:
    value = data.get(key, default)
    if value is None:
        raise SyncValidationError(f"Missing field: {key}")
    try:
        return float(value)
    except (TypeError, ValueError) as exc:
        raise SyncValidationError(f"Invalid number field: {key}") from exc


def required_int(data: dict[str, Any], key: str, default: int | None = None) -> int:
    value = data.get(key, default)
    if value is None:
        raise SyncValidationError(f"Missing field: {key}")
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise SyncValidationError(f"Invalid int field: {key}") from exc


def parse_transaction_type(value: str) -> TransactionType:
    try:
        return TransactionType(value.lower())
    except ValueError as exc:
        raise SyncValidationError("Invalid transaction type") from exc


def parse_source_type(value: str) -> SourceType:
    try:
        return SourceType(value.lower())
    except ValueError as exc:
        raise SyncValidationError("Invalid source type") from exc


def parse_datetime(value: str) -> datetime:
    normalized = value.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise SyncValidationError("Invalid datetime") from exc
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def parse_cursor(cursor: str | None) -> int | None:
    if cursor is None or not cursor.strip():
        return None
    try:
        return int(cursor)
    except ValueError as exc:
        raise SyncValidationError("Invalid cursor") from exc


def to_iso(value: datetime | None) -> str | None:
    if value is None:
        return None
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")


def to_float(value: Decimal | float | int) -> float:
    return float(value)

from __future__ import annotations

from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import (
    TokenError,
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)
from app.models.entities import (
    Account,
    Book,
    BookMembership,
    Category,
    Device,
    MembershipRole,
    Member,
    TransactionType,
    User,
)
from app.schemas.auth import AuthSessionResponse, BookSummary, LoginRequest, RegisterRequest, UserSummary
from app.schemas.bootstrap import BootstrapResponse
from app.services.sync_service import log_entity_change, serialize_entity


class AuthenticationError(ValueError):
    """Raised for invalid credentials or tokens."""


class DuplicateUsernameError(ValueError):
    """Raised when a username already exists."""


def normalize_username(username: str) -> str:
    return username.strip().lower()


async def create_user_with_defaults(
    db: AsyncSession,
    payload: RegisterRequest,
) -> AuthSessionResponse:
    username = normalize_username(payload.username)
    existing = await db.execute(select(User).where(User.username == username))
    if existing.scalar_one_or_none() is not None:
        raise DuplicateUsernameError("Username already exists")

    user = User(
        username=username,
        display_name=payload.display_name.strip(),
        password_hash=hash_password(payload.password),
    )
    db.add(user)
    await db.flush()

    book = Book(
        name=f"{user.display_name}的家庭账本",
        owner_user_id=user.id,
    )
    db.add(book)
    await db.flush()

    db.add(
        BookMembership(
            book_id=book.id,
            user_id=user.id,
            role=MembershipRole.OWNER,
        )
    )

    member = Member(book_id=book.id, name=user.display_name)
    db.add(member)

    accounts: list[Account] = []
    for account_name, initial_balance in [("微信", 0), ("现金", 0)]:
        account = Account(
            book_id=book.id,
            name=account_name,
            initial_balance=initial_balance,
        )
        db.add(account)
        accounts.append(account)

    income_categories = ["工资", "奖金", "报销", "其他收入"]
    expense_categories = ["餐饮", "购物", "交通", "居家", "医疗", "教育", "娱乐", "人情", "其他支出"]

    categories: list[Category] = []
    for index, name in enumerate(income_categories):
        category = Category(
            book_id=book.id,
            type=TransactionType.INCOME,
            name=name,
            sort_order=index,
        )
        db.add(category)
        categories.append(category)

    for index, name in enumerate(expense_categories):
        category = Category(
            book_id=book.id,
            type=TransactionType.EXPENSE,
            name=name,
            sort_order=index,
        )
        db.add(category)
        categories.append(category)

    if payload.device_name:
        db.add(
            Device(
                user_id=user.id,
                device_name=payload.device_name,
                platform="android",
                last_seen_at=datetime.now(UTC),
            )
        )

    await db.flush()
    await log_entity_change(
        db,
        book_id=book.id,
        entity_type="book",
        operation_type="upsert",
        entity_id=book.id,
        payload=serialize_entity("book", book),
        client_mutation_id=f"seed-book-{book.id}",
        operator_user_id=user.id,
    )
    await log_entity_change(
        db,
        book_id=book.id,
        entity_type="member",
        operation_type="upsert",
        entity_id=member.id,
        payload=serialize_entity("member", member),
        client_mutation_id=f"seed-member-{member.id}",
        operator_user_id=user.id,
    )
    for account in accounts:
        await log_entity_change(
            db,
            book_id=book.id,
            entity_type="account",
            operation_type="upsert",
            entity_id=account.id,
            payload=serialize_entity("account", account),
            client_mutation_id=f"seed-account-{account.id}",
            operator_user_id=user.id,
        )
    for category in categories:
        await log_entity_change(
            db,
            book_id=book.id,
            entity_type="category",
            operation_type="upsert",
            entity_id=category.id,
            payload=serialize_entity("category", category),
            client_mutation_id=f"seed-category-{category.id}",
            operator_user_id=user.id,
        )

    await db.commit()
    await db.refresh(user)
    books = await list_books_for_user(db, user.id)
    return AuthSessionResponse(
        access_token=create_access_token(user.id),
        refresh_token=create_refresh_token(user.id),
        user=UserSummary.model_validate(user),
        books=books,
    )


async def login_user(
    db: AsyncSession,
    payload: LoginRequest,
) -> AuthSessionResponse:
    username = normalize_username(payload.username)
    result = await db.execute(select(User).where(User.username == username))
    user = result.scalar_one_or_none()
    if user is None or not verify_password(payload.password, user.password_hash):
        raise AuthenticationError("Invalid username or password")

    if payload.device_name:
        await upsert_device(db, user.id, payload.device_name)

    books = await list_books_for_user(db, user.id)
    return AuthSessionResponse(
        access_token=create_access_token(user.id),
        refresh_token=create_refresh_token(user.id),
        user=UserSummary.model_validate(user),
        books=books,
    )


async def issue_tokens_for_refresh_token(
    db: AsyncSession,
    refresh_token: str,
) -> AuthSessionResponse:
    try:
        payload = decode_token(refresh_token, expected_type="refresh")
    except TokenError as exc:
        raise AuthenticationError(str(exc)) from exc

    result = await db.execute(select(User).where(User.id == payload.subject))
    user = result.scalar_one_or_none()
    if user is None:
        raise AuthenticationError("User not found")

    books = await list_books_for_user(db, user.id)
    return AuthSessionResponse(
        access_token=create_access_token(user.id),
        refresh_token=create_refresh_token(user.id),
        user=UserSummary.model_validate(user),
        books=books,
    )


async def build_bootstrap_payload(db: AsyncSession, user: User) -> BootstrapResponse:
    books = await list_books_for_user(db, user.id)
    return BootstrapResponse(
        user=UserSummary.model_validate(user),
        books=books,
    )


async def list_books_for_user(db: AsyncSession, user_id: str) -> list[BookSummary]:
    stmt = (
        select(Book, BookMembership)
        .join(BookMembership, BookMembership.book_id == Book.id)
        .where(
            BookMembership.user_id == user_id,
            Book.deleted_at.is_(None),
        )
        .order_by(Book.created_at.asc())
    )
    rows = (await db.execute(stmt)).all()
    return [
        BookSummary(
            id=book.id,
            name=book.name,
            role=membership.role.value if hasattr(membership.role, "value") else str(membership.role),
        )
        for book, membership in rows
    ]


async def upsert_device(db: AsyncSession, user_id: str, device_name: str) -> None:
    stmt = select(Device).where(
        Device.user_id == user_id,
        Device.device_name == device_name,
    )
    existing = (await db.execute(stmt)).scalar_one_or_none()
    if existing is None:
        db.add(
            Device(
                user_id=user_id,
                device_name=device_name,
                platform="android",
                last_seen_at=datetime.now(UTC),
            )
        )
    else:
        existing.last_seen_at = datetime.now(UTC)
    await db.commit()

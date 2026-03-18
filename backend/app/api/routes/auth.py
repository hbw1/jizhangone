from __future__ import annotations

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.errors import api_http_exception
from app.db.session import get_db
from app.schemas.auth import (
    AuthSessionResponse,
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
)
from app.services.auth_service import (
    AuthenticationError,
    DuplicateUsernameError,
    create_user_with_defaults,
    issue_tokens_for_refresh_token,
    login_user,
)

router = APIRouter()


@router.post("/register", response_model=AuthSessionResponse, status_code=status.HTTP_201_CREATED)
async def register(
    payload: RegisterRequest,
    db: AsyncSession = Depends(get_db),
) -> AuthSessionResponse:
    try:
        return await create_user_with_defaults(db, payload)
    except DuplicateUsernameError as exc:
        raise api_http_exception(
            status_code=status.HTTP_409_CONFLICT,
            code="auth_username_exists",
            message="这个账号已经注册过了",
            details={"field": "username", "reason": str(exc)},
        ) from exc


@router.post("/login", response_model=AuthSessionResponse)
async def login(
    payload: LoginRequest,
    db: AsyncSession = Depends(get_db),
) -> AuthSessionResponse:
    try:
        return await login_user(db, payload)
    except AuthenticationError as exc:
        raise api_http_exception(
            status_code=status.HTTP_401_UNAUTHORIZED,
            code="auth_invalid_credentials",
            message="账号或密码错误",
            details={"reason": str(exc)},
        ) from exc


@router.post("/refresh", response_model=AuthSessionResponse)
async def refresh(
    payload: RefreshRequest,
    db: AsyncSession = Depends(get_db),
) -> AuthSessionResponse:
    try:
        return await issue_tokens_for_refresh_token(db, payload.refresh_token)
    except AuthenticationError as exc:
        raise api_http_exception(
            status_code=status.HTTP_401_UNAUTHORIZED,
            code="auth_refresh_failed",
            message="登录已失效，请重新登录",
            details={"reason": str(exc)},
        ) from exc

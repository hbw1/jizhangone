from __future__ import annotations

from fastapi import Depends, Header, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.errors import api_http_exception
from app.core.security import TokenError, decode_token
from app.db.session import get_db
from app.models.entities import User


async def get_current_user(
    authorization: str | None = Header(default=None),
    app_authorization: str | None = Header(default=None, alias="X-App-Authorization"),
    db: AsyncSession = Depends(get_db),
) -> User:
    raw_authorization = app_authorization or authorization
    if raw_authorization is None or not raw_authorization.strip():
        raise api_http_exception(
            status_code=status.HTTP_401_UNAUTHORIZED,
            code="auth_missing_token",
            message="请先登录云端账号",
        )

    scheme, _, token = raw_authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise api_http_exception(
            status_code=status.HTTP_401_UNAUTHORIZED,
            code="auth_invalid_token",
            message="登录已失效，请重新登录",
            details={"reason": "Missing Bearer token"},
        )

    try:
        payload = decode_token(token, expected_type="access")
    except TokenError as exc:
        raise api_http_exception(
            status_code=status.HTTP_401_UNAUTHORIZED,
            code="auth_invalid_token",
            message="登录已失效，请重新登录",
            details={"reason": str(exc)},
        ) from exc

    result = await db.execute(select(User).where(User.id == payload.subject))
    user = result.scalar_one_or_none()
    if user is None:
        raise api_http_exception(
            status_code=status.HTTP_401_UNAUTHORIZED,
            code="auth_user_not_found",
            message="当前登录用户不存在",
        )
    return user

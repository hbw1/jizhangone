from __future__ import annotations

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.errors import api_http_exception
from app.api.deps import get_current_user
from app.db.session import get_db
from app.models.entities import User
from app.schemas.sync import SyncPullResponse, SyncPushRequest, SyncPushResponse
from app.services.sync_service import SyncPermissionError, SyncValidationError, pull_changes, push_changes

router = APIRouter()


@router.post("/push", response_model=SyncPushResponse)
async def sync_push(
    payload: SyncPushRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> SyncPushResponse:
    try:
        return await push_changes(db, current_user, payload)
    except SyncPermissionError as exc:
        raise api_http_exception(
            status_code=status.HTTP_403_FORBIDDEN,
            code="sync_book_forbidden",
            message="当前账本没有同步权限",
            details={"reason": str(exc)},
        ) from exc
    except SyncValidationError as exc:
        raise api_http_exception(
            status_code=status.HTTP_400_BAD_REQUEST,
            code="sync_invalid_payload",
            message="同步请求不合法",
            details={"reason": str(exc)},
        ) from exc


@router.get("/pull", response_model=SyncPullResponse)
async def sync_pull(
    book_id: str = Query(...),
    cursor: str | None = Query(default=None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> SyncPullResponse:
    try:
        return await pull_changes(db, current_user, book_id=book_id, cursor=cursor)
    except SyncPermissionError as exc:
        raise api_http_exception(
            status_code=status.HTTP_403_FORBIDDEN,
            code="sync_book_forbidden",
            message="当前账本没有同步权限",
            details={"reason": str(exc)},
        ) from exc
    except SyncValidationError as exc:
        raise api_http_exception(
            status_code=status.HTTP_400_BAD_REQUEST,
            code="sync_invalid_request",
            message="同步参数不合法",
            details={"reason": str(exc)},
        ) from exc

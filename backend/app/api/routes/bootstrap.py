from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models.entities import User
from app.schemas.bootstrap import BootstrapResponse
from app.services.auth_service import build_bootstrap_payload

router = APIRouter()


@router.get("/bootstrap", response_model=BootstrapResponse)
async def bootstrap(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> BootstrapResponse:
    return await build_bootstrap_payload(db, current_user)

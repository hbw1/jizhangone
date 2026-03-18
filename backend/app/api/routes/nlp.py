from __future__ import annotations

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.errors import api_http_exception
from app.api.deps import get_current_user
from app.db.session import get_db
from app.models.entities import User
from app.schemas.nlp import CloudParseRequest, CloudParseResponse
from app.services.nlp_service import (
    NlpPermissionError,
    NlpProviderConfigurationError,
    NlpProviderResponseError,
    parse_natural_language_with_minimax,
)

router = APIRouter()


@router.post("/parse-natural-language", response_model=CloudParseResponse)
async def parse_natural_language(
    payload: CloudParseRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CloudParseResponse:
    try:
        return await parse_natural_language_with_minimax(db, current_user, payload)
    except NlpPermissionError as exc:
        raise api_http_exception(
            status_code=status.HTTP_403_FORBIDDEN,
            code="nlp_book_forbidden",
            message="当前账本没有智能解析权限",
            details={"reason": str(exc)},
        ) from exc
    except NlpProviderConfigurationError as exc:
        raise api_http_exception(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            code="nlp_provider_not_configured",
            message="云端智能解析还没有配置好",
            details={"provider": "minimax", "reason": str(exc)},
        ) from exc
    except NlpProviderResponseError as exc:
        raise api_http_exception(
            status_code=status.HTTP_502_BAD_GATEWAY,
            code="nlp_provider_failed",
            message="智能解析服务暂时不可用",
            details={"provider": "minimax", "reason": str(exc)},
        ) from exc

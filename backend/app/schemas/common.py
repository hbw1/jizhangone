from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    app: str
    environment: str
    database_ok: bool


class ApiError(BaseModel):
    code: str
    message: str
    request_id: str
    details: Any | None = None


class ApiErrorResponse(BaseModel):
    error: ApiError

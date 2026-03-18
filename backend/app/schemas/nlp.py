from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

from app.models.entities import TransactionType


class CloudParseRequest(BaseModel):
    book_id: str
    raw_text: str = Field(min_length=1, max_length=4000)
    today: date | None = None
    timezone: str = Field(default="Asia/Shanghai", max_length=64)


class CloudParsedTransactionCandidate(BaseModel):
    type: TransactionType
    amount: float | None = None
    occurred_on: date | None = None
    member_name: str | None = None
    category_name: str | None = None
    note: str = ""
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    source_snippet: str = ""


class CloudParseResponse(BaseModel):
    provider: str = "minimax"
    model: str
    raw_text: str
    diary_text: str
    candidates: list[CloudParsedTransactionCandidate]
    warnings: list[str] = Field(default_factory=list)

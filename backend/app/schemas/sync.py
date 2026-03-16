from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class SyncOperationRequest(BaseModel):
    client_mutation_id: str = Field(min_length=1, max_length=64)
    entity_type: Literal["book", "member", "account", "category", "journal_entry", "transaction"]
    operation_type: Literal["upsert", "delete"]
    payload: dict[str, Any] = Field(default_factory=dict)


class SyncPushRequest(BaseModel):
    book_id: str
    operations: list[SyncOperationRequest]


class SyncOperationAck(BaseModel):
    sequence: int
    entity_type: str
    operation_type: str
    entity_id: str
    client_mutation_id: str
    status: Literal["applied", "duplicate"]


class SyncPushResponse(BaseModel):
    book_id: str
    applied: list[SyncOperationAck]
    next_cursor: str


class SyncChange(BaseModel):
    sequence: int
    entity_type: str
    operation_type: str
    entity_id: str
    client_mutation_id: str
    created_at: datetime
    payload: dict[str, Any]


class SyncPullResponse(BaseModel):
    book_id: str
    changes: list[SyncChange]
    next_cursor: str

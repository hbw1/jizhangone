from __future__ import annotations

from pydantic import BaseModel

from app.schemas.auth import BookSummary, UserSummary


class BootstrapResponse(BaseModel):
    user: UserSummary
    books: list[BookSummary]
    sync_enabled: bool = True

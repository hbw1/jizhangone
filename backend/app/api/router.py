from __future__ import annotations

from fastapi import APIRouter

from app.api.routes import auth, bootstrap, health, sync

api_router = APIRouter()
api_router.include_router(health.router, tags=["health"])

v1_router = APIRouter(prefix="/v1")
v1_router.include_router(auth.router, prefix="/auth", tags=["auth"])
v1_router.include_router(bootstrap.router, tags=["bootstrap"])
v1_router.include_router(sync.router, prefix="/sync", tags=["sync"])

api_router.include_router(v1_router)

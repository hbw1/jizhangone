from __future__ import annotations

from typing import Any

from fastapi import HTTPException, Request, status
from fastapi.responses import JSONResponse


def api_http_exception(
    status_code: int,
    code: str,
    message: str,
    details: Any | None = None,
) -> HTTPException:
    return HTTPException(
        status_code=status_code,
        detail={
            "code": code,
            "message": message,
            "details": details,
        },
    )


def api_error_response(
    request: Request,
    status_code: int,
    code: str,
    message: str,
    details: Any | None = None,
) -> JSONResponse:
    request_id = getattr(request.state, "request_id", "unknown")
    payload = {
        "error": {
            "code": code,
            "message": message,
            "request_id": request_id,
            "details": details,
        }
    }
    return JSONResponse(
        status_code=status_code,
        headers={"X-Request-ID": request_id},
        content=payload,
    )


def normalize_http_error(status_code: int, detail: Any) -> tuple[str, str, Any | None]:
    if isinstance(detail, dict):
        code = str(detail.get("code") or default_error_code(status_code))
        message = str(detail.get("message") or default_error_message(status_code))
        details = detail.get("details")
        return code, message, details

    if isinstance(detail, str) and detail.strip():
        return default_error_code(status_code), detail, None

    return default_error_code(status_code), default_error_message(status_code), None


def default_error_code(status_code: int) -> str:
    return {
        status.HTTP_400_BAD_REQUEST: "bad_request",
        status.HTTP_401_UNAUTHORIZED: "unauthorized",
        status.HTTP_403_FORBIDDEN: "forbidden",
        status.HTTP_404_NOT_FOUND: "not_found",
        status.HTTP_409_CONFLICT: "conflict",
        status.HTTP_422_UNPROCESSABLE_ENTITY: "validation_error",
        status.HTTP_500_INTERNAL_SERVER_ERROR: "internal_server_error",
        status.HTTP_502_BAD_GATEWAY: "upstream_service_error",
        status.HTTP_503_SERVICE_UNAVAILABLE: "service_unavailable",
    }.get(status_code, "request_failed")


def default_error_message(status_code: int) -> str:
    return {
        status.HTTP_400_BAD_REQUEST: "请求不合法",
        status.HTTP_401_UNAUTHORIZED: "登录已失效，请重新登录",
        status.HTTP_403_FORBIDDEN: "当前没有权限执行这个操作",
        status.HTTP_404_NOT_FOUND: "请求的资源不存在",
        status.HTTP_409_CONFLICT: "请求的资源已存在或发生冲突",
        status.HTTP_422_UNPROCESSABLE_ENTITY: "请求参数校验失败",
        status.HTTP_500_INTERNAL_SERVER_ERROR: "服务器开小差了，请稍后重试",
        status.HTTP_502_BAD_GATEWAY: "上游服务暂时不可用",
        status.HTTP_503_SERVICE_UNAVAILABLE: "服务暂时不可用，请稍后再试",
    }.get(status_code, "请求失败")

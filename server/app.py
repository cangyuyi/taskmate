"""FastAPI 路由与鉴权层。

启动方式（推荐）：python3 main.py
也可以：uvicorn 'app:create_app' --factory --host 0.0.0.0 --port 8788

设计约定：
- 业务错误统一返回 {"ok": false, "error": "..."}
- 鉴权失败统一返回 401 {"ok": false, "error": "unauthorized"}
- /api/* 全部需要 X-API-Key；/ 与 /healthz 不需要
"""

import json
import logging
import secrets
from typing import Any, Dict, Optional

from fastapi import APIRouter, FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse

from config import Settings, load_settings
from service import ServiceError, TaskService, ValidationError
from store import Store

logger = logging.getLogger("taskmate.app")


async def _read_json(request: Request) -> Dict[str, Any]:
    raw = await request.body()
    if not raw:
        raise ValidationError("请求体不能为空")
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        raise ValidationError("请求体不是合法的 JSON")
    if not isinstance(data, dict):
        raise ValidationError("请求体必须是 JSON 对象")
    return data


def create_app(settings: Optional[Settings] = None) -> FastAPI:
    settings = settings or load_settings()

    app = FastAPI(
        title="任务助理同步服务",
        description="TaskMate 个人自用任务同步服务与网页控制台",
        version="1.0.0",
        docs_url=None,
        redoc_url=None,
    )

    store = Store(settings.db_path)
    service = TaskService(store)
    app.state.settings = settings
    app.state.store = store
    app.state.service = service

    @app.middleware("http")
    async def _api_key_guard(request: Request, call_next):
        """所有 /api/* 统一鉴权（含不存在的路径），/ 与 /healthz 放行。"""
        path = request.url.path
        if path == "/api" or path.startswith("/api/"):
            provided = request.headers.get("x-api-key") or ""
            expected = settings.api_key or ""
            if not expected or not provided or not secrets.compare_digest(provided, expected):
                return JSONResponse(status_code=401, content={"ok": False, "error": "unauthorized"})
        return await call_next(request)

    @app.exception_handler(ServiceError)
    async def _handle_service_error(request: Request, exc: ServiceError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content={"ok": False, "error": exc.message},
        )

    @app.exception_handler(Exception)
    async def _handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("未处理的异常：%s", exc)
        return JSONResponse(status_code=500, content={"ok": False, "error": "internal error"})

    api = APIRouter(prefix="/api")

    # ------------------------------------------------------------------ sync

    @api.post("/sync")
    async def sync(request: Request) -> JSONResponse:
        payload = await _read_json(request)
        result = service.sync(
            device_id=payload.get("device_id"),
            since=payload.get("since", 0),
            tasks=payload.get("tasks", []),
            platform=payload.get("platform", ""),
        )
        return JSONResponse(content=result)

    # ----------------------------------------------------------------- tasks

    @api.get("/tasks")
    async def list_tasks(
        include_deleted: str = "0",
        filter: str = "all",
    ) -> JSONResponse:
        tasks = service.list_tasks(
            include_deleted=include_deleted,
            status_filter=filter,
        )
        return JSONResponse(content={"ok": True, "tasks": tasks})

    @api.post("/tasks")
    async def create_task(request: Request) -> JSONResponse:
        payload = await _read_json(request)
        task = service.create_task(payload)
        return JSONResponse(content={"ok": True, "task": task})

    @api.patch("/tasks/{task_id}")
    async def patch_task(task_id: str, request: Request) -> JSONResponse:
        payload = await _read_json(request)
        task = service.patch_task(task_id, payload)
        return JSONResponse(content={"ok": True, "task": task})

    @api.delete("/tasks/{task_id}")
    async def delete_task(task_id: str) -> JSONResponse:
        service.delete_task(task_id)
        return JSONResponse(content={"ok": True})

    @api.get("/stats")
    async def stats() -> JSONResponse:
        return JSONResponse(content=service.stats())

    app.include_router(api)

    # ------------------------------------------------------------- 无需鉴权

    @app.get("/healthz")
    async def healthz() -> JSONResponse:
        return JSONResponse(content=service.health())

    @app.get("/", response_class=HTMLResponse)
    async def dashboard() -> HTMLResponse:
        index_file = settings.dashboard_file
        try:
            html = index_file.read_text(encoding="utf-8")
        except OSError:
            logger.error("控制台页面缺失：%s", index_file)
            return HTMLResponse(
                "<!doctype html><meta charset='utf-8'>"
                "<h1>控制台页面缺失</h1>"
                "<p>请确认 static/index.html 存在。</p>",
                status_code=500,
            )
        return HTMLResponse(content=html, headers={"Cache-Control": "no-store"})

    return app


__all__ = ["create_app"]
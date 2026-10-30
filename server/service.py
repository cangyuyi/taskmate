"""业务层：任务合并、CRUD、统计。

刻意不 import fastapi，任何 HTTP 框架之外都可以离线测试。
所有时间字段都是 epoch 毫秒（int）。
"""

import time
import uuid
from datetime import datetime
from typing import Any, Callable, Dict, List, Optional

from store import Store

STATUS_FILTERS = ("all", "active", "done")
DAY_MS = 24 * 60 * 60 * 1000


class ServiceError(Exception):
    """业务异常基类，app.py 会把它转换成对应状态码的 JSON。"""

    status_code = 400

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class ValidationError(ServiceError):
    status_code = 400


class NotFoundError(ServiceError):
    status_code = 404


def now_ms() -> int:
    return int(time.time() * 1000)


def local_day_start_ms(epoch_ms: int) -> int:
    """给定时刻当天的本地 0 点（epoch 毫秒）。"""
    moment = datetime.fromtimestamp(epoch_ms / 1000.0)
    start = datetime(moment.year, moment.month, moment.day)
    return int(start.timestamp() * 1000)


# --------------------------------------------------------------------- 校验


def _as_int(value: Any, field: str, default: Optional[int] = None) -> Optional[int]:
    if value is None:
        return default
    if isinstance(value, bool):
        raise ValidationError("{0} 必须是整数".format(field))
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        if not value.is_integer():
            raise ValidationError("{0} 必须是整数".format(field))
        return int(value)
    raise ValidationError("{0} 必须是整数".format(field))


def _as_text(value: Any, field: str, default: str = "") -> str:
    if value is None:
        return default
    if not isinstance(value, str):
        raise ValidationError("{0} 必须是字符串".format(field))
    return value


def _as_bool(value: Any, field: str, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, int) and value in (0, 1):
        return bool(value)
    if isinstance(value, float) and value in (0.0, 1.0):
        return bool(int(value))
    raise ValidationError("{0} 必须是布尔值".format(field))


REPEAT_RULES = {"", "daily", "weekdays", "weekly", "monthly"}


def _as_repeat(value: Any) -> str:
    """重复规则；不认识的值一律当作不重复，避免脏数据。"""
    if value is None:
        return ""
    text = str(value).strip().lower()
    return text if text in REPEAT_RULES else ""


def _as_priority(value: Any, default: int = 2) -> int:
    priority = _as_int(value, "priority", default=default)
    if priority is None:
        priority = default
    # 容错：极端数值收敛到 1~3，避免手机端枚举映射出错时任务丢失。
    return max(1, min(3, priority))


def _clean_title(value: Any, field: str = "title") -> str:
    title = _as_text(value, field).strip()
    if not title:
        raise ValidationError("标题不能为空")
    return title


def task_to_json(row: Dict[str, Any]) -> Dict[str, Any]:
    """数据库行 -> 对外 TaskJSON（与手机端约定字段完全一致）。"""
    return {
        "id": row["id"],
        "title": row["title"],
        "notes": row["notes"] or "",
        "due_at": row["due_at"],
        "remind_at": row["remind_at"],
        "priority": int(row["priority"]),
        "repeat_rule": (row.get("repeat_rule") or ""),
        "done": bool(row["done"]),
        "deleted": bool(row["deleted"]),
        "updated_at": int(row["updated_at"]),
    }


class TaskService:
    def __init__(self, store: Store, clock: Optional[Callable[[], int]] = None) -> None:
        self.store = store
        self._clock = clock or now_ms

    # ------------------------------------------------------------- 内部工具

    def _now(self) -> int:
        return int(self._clock())

    def _normalise_sync_task(self, raw: Any) -> Dict[str, Any]:
        if not isinstance(raw, dict):
            raise ValidationError("tasks 中的每一项都必须是对象")

        task_id = raw.get("id")
        if not isinstance(task_id, str) or not task_id.strip():
            raise ValidationError("task.id 必须是非空字符串")

        updated_at = _as_int(raw.get("updated_at"), "updated_at")
        if updated_at is None:
            raise ValidationError("updated_at 不能为空")

        created_at = _as_int(raw.get("created_at"), "created_at")
        if created_at is None:
            created_at = updated_at

        return {
            "id": task_id.strip(),
            "title": _as_text(raw.get("title"), "title", default=""),
            "notes": _as_text(raw.get("notes"), "notes", default=""),
            "due_at": _as_int(raw.get("due_at"), "due_at"),
            "remind_at": _as_int(raw.get("remind_at"), "remind_at"),
            "priority": _as_priority(raw.get("priority")),
            "repeat_rule": _as_repeat(raw.get("repeat_rule")),
            "done": _as_bool(raw.get("done"), "done"),
            "deleted": _as_bool(raw.get("deleted"), "deleted"),
            "updated_at": updated_at,
            "created_at": created_at,
        }

    # ------------------------------------------------------------------ sync

    def sync(
        self,
        device_id: Any,
        since: Any,
        tasks: Any,
        platform: str = "",
    ) -> Dict[str, Any]:
        if not isinstance(device_id, str) or not device_id.strip():
            raise ValidationError("device_id 必须是非空字符串")
        device_id = device_id.strip()

        since_value = _as_int(since, "since", default=0)
        if since_value is None:
            since_value = 0
        if since_value < 0:
            since_value = 0

        if tasks is None:
            tasks = []
        if not isinstance(tasks, list):
            raise ValidationError("tasks 必须是数组")

        # 先整体校验，避免一半写入、一半报错。
        incoming = [self._normalise_sync_task(item) for item in tasks]
        platform_text = _as_text(platform, "platform", default="")
        now = self._now()

        with self.store.transaction() as conn:
            for item in incoming:
                existing = self.store.get_task(conn, item["id"])
                if existing is None:
                    self.store.insert_task(conn, item)
                elif item["updated_at"] > int(existing["updated_at"]):
                    # 服务端时间戳不倒退：created_at 保持首次写入的值。
                    fields = {
                        "title": item["title"],
                        "notes": item["notes"],
                        "due_at": item["due_at"],
                        "remind_at": item["remind_at"],
                        "priority": item["priority"],
                        "done": 1 if item["done"] else 0,
                        "deleted": 1 if item["deleted"] else 0,
                        "updated_at": item["updated_at"],
                    }
                    self.store.update_task(conn, item["id"], fields)
                # 相等或更旧：忽略（服务端为准），仍会出现在增量返回里。

            self.store.touch_device(conn, device_id, now, platform_text)
            rows = self.store.list_tasks(conn, include_deleted=True, updated_after=since_value)

        return {
            "ok": True,
            "now": now,
            "tasks": [task_to_json(row) for row in rows],
        }

    # ------------------------------------------------------------------ 查询

    def list_tasks(self, include_deleted: Any = False, status_filter: str = "all") -> List[Dict[str, Any]]:
        if status_filter not in STATUS_FILTERS:
            raise ValidationError("filter 只能是 all / active / done")
        if isinstance(include_deleted, str):
            include_deleted_bool = include_deleted.strip().lower() in ("1", "true", "yes")
        else:
            include_deleted_bool = _as_bool(include_deleted, "include_deleted")
        with self.store.transaction() as conn:
            rows = self.store.list_tasks(
                conn,
                include_deleted=include_deleted_bool,
                status_filter=status_filter,
            )
        return [task_to_json(row) for row in rows]

    def get_task(self, task_id: str) -> Dict[str, Any]:
        with self.store.transaction() as conn:
            row = self.store.get_task(conn, task_id)
        if row is None:
            raise NotFoundError("任务不存在")
        return task_to_json(row)

    # ------------------------------------------------------------------ 写入

    def create_task(self, payload: Any) -> Dict[str, Any]:
        if not isinstance(payload, dict):
            raise ValidationError("请求体必须是 JSON 对象")

        title = _clean_title(payload.get("title"))
        notes = _as_text(payload.get("notes"), "notes", default="")
        due_at = _as_int(payload.get("due_at"), "due_at")
        remind_at = _as_int(payload.get("remind_at"), "remind_at")
        priority = _as_priority(payload.get("priority"))
        repeat_rule = _as_repeat(payload.get("repeat_rule"))
        now = self._now()

        record = {
            "id": str(uuid.uuid4()),
            "title": title,
            "notes": notes,
            "due_at": due_at,
            "remind_at": remind_at,
            "priority": priority,
            "repeat_rule": repeat_rule,
            "done": 0,
            "deleted": 0,
            "updated_at": now,
            "created_at": now,
        }
        with self.store.transaction() as conn:
            self.store.insert_task(conn, record)
        return task_to_json(record)

    def patch_task(self, task_id: str, payload: Any) -> Dict[str, Any]:
        if not isinstance(payload, dict):
            raise ValidationError("请求体必须是 JSON 对象")

        fields: Dict[str, Any] = {}

        if "title" in payload:
            fields["title"] = _clean_title(payload.get("title"))
        if "notes" in payload:
            fields["notes"] = _as_text(payload.get("notes"), "notes", default="")
        if "due_at" in payload:
            fields["due_at"] = _as_int(payload.get("due_at"), "due_at")
        if "remind_at" in payload:
            fields["remind_at"] = _as_int(payload.get("remind_at"), "remind_at")
        if "priority" in payload:
            fields["priority"] = _as_priority(payload.get("priority"))
        if "repeat_rule" in payload:
            fields["repeat_rule"] = _as_repeat(payload.get("repeat_rule"))
        if "done" in payload:
            fields["done"] = 1 if _as_bool(payload.get("done"), "done") else 0
        if "deleted" in payload:
            fields["deleted"] = 1 if _as_bool(payload.get("deleted"), "deleted") else 0

        fields["updated_at"] = self._now()

        with self.store.transaction() as conn:
            existing = self.store.get_task(conn, task_id)
            if existing is None:
                raise NotFoundError("任务不存在")
            self.store.update_task(conn, task_id, fields)
            row = self.store.get_task(conn, task_id)
        if row is None:
            raise NotFoundError("任务不存在")
        return task_to_json(row)

    def delete_task(self, task_id: str) -> None:
        now = self._now()
        with self.store.transaction() as conn:
            existing = self.store.get_task(conn, task_id)
            if existing is None:
                raise NotFoundError("任务不存在")
            self.store.update_task(conn, task_id, {"deleted": 1, "updated_at": now})

    # ------------------------------------------------------------------ 统计

    def stats(self) -> Dict[str, Any]:
        now = self._now()
        day_start = local_day_start_ms(now)
        tomorrow_start = day_start + DAY_MS
        week_end = day_start + 7 * DAY_MS
        with self.store.transaction() as conn:
            counts = self.store.stats_counts(conn, day_start, tomorrow_start, week_end)
        return {
            "ok": True,
            "total": counts["total"],
            "active": counts["active"],
            "done": counts["done"],
            "overdue": counts["overdue"],
            "today": counts["today"],
            "week": counts["week"],
        }

    def health(self) -> Dict[str, Any]:
        with self.store.transaction() as conn:
            tasks = self.store.count_tasks(conn, include_deleted=False)
            devices = self.store.count_devices(conn)
        return {"ok": True, "tasks": tasks, "devices": devices}


__all__ = [
    "TaskService",
    "ServiceError",
    "ValidationError",
    "NotFoundError",
    "task_to_json",
    "now_ms",
    "local_day_start_ms",
    "DAY_MS",
]
"""SQLite 持久化层。

只负责建表、读写与事务，不包含任何业务判断，方便离线单测。
连接按“每次操作新开一个连接”的方式管理：个人自用规模下足够快，
也避免了多线程（FastAPI 线程池）共享连接的问题。
"""

import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Dict, Iterator, List, Optional

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS tasks (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    notes       TEXT NOT NULL DEFAULT '',
    due_at      INTEGER,
    remind_at   INTEGER,
    priority    INTEGER NOT NULL DEFAULT 2,
    repeat_rule TEXT NOT NULL DEFAULT '',
    done        INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tasks_updated_at ON tasks(updated_at);
CREATE INDEX IF NOT EXISTS idx_tasks_due_at ON tasks(due_at);
CREATE INDEX IF NOT EXISTS idx_tasks_deleted ON tasks(deleted);

CREATE TABLE IF NOT EXISTS devices (
    id         TEXT PRIMARY KEY,
    last_seen  INTEGER,
    platform   TEXT
);
"""

MIGRATIONS = (
    ("repeat_rule", "ALTER TABLE tasks ADD COLUMN repeat_rule TEXT NOT NULL DEFAULT ''"),
)

TASK_COLUMNS = (
    "id",
    "title",
    "notes",
    "due_at",
    "remind_at",
    "priority",
    "repeat_rule",
    "done",
    "deleted",
    "updated_at",
    "created_at",
)


def _row_to_dict(row: Optional[sqlite3.Row]) -> Optional[Dict[str, Any]]:
    return dict(row) if row is not None else None


class Store:
    """SQLite 存取器。所有写操作都通过 transaction() 控制提交/回滚。"""

    def __init__(self, db_path: str) -> None:
        self.db_path = str(db_path)
        self._lock = threading.RLock()
        parent = Path(self.db_path).expanduser().parent
        if str(parent):
            parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    # ------------------------------------------------------------------ 连接

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=15.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA busy_timeout = 5000")
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    @contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        """写事务：BEGIN IMMEDIATE 保证“先读后写”期间不会被别的写者插队。"""
        with self._lock:
            conn = self._connect()
            try:
                conn.execute("BEGIN IMMEDIATE")
                yield conn
                conn.commit()
            except BaseException:
                conn.rollback()
                raise
            finally:
                conn.close()

    def _init_schema(self) -> None:
        conn = self._connect()
        try:
            conn.execute("PRAGMA journal_mode = WAL")
            conn.execute("PRAGMA synchronous = NORMAL")
            conn.executescript(SCHEMA_SQL)
            # 老库升级：缺列就补（CREATE TABLE IF NOT EXISTS 不会改已存在的表）
            existing = {row["name"] for row in conn.execute("PRAGMA table_info(tasks)").fetchall()}
            for column, ddl in MIGRATIONS:
                if column not in existing:
                    conn.execute(ddl)
            conn.commit()
        finally:
            conn.close()

    # ------------------------------------------------------------------ tasks

    def get_task(self, conn: sqlite3.Connection, task_id: str) -> Optional[Dict[str, Any]]:
        row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
        return _row_to_dict(row)

    def insert_task(self, conn: sqlite3.Connection, record: Dict[str, Any]) -> None:
        placeholders = ", ".join("?" for _ in TASK_COLUMNS)
        columns = ", ".join(TASK_COLUMNS)
        conn.execute(
            "INSERT INTO tasks ({cols}) VALUES ({ph})".format(cols=columns, ph=placeholders),
            tuple(record[column] for column in TASK_COLUMNS),
        )

    def update_task(self, conn: sqlite3.Connection, task_id: str, fields: Dict[str, Any]) -> None:
        if not fields:
            return
        assignments = ", ".join("{0} = ?".format(name) for name in fields)
        values = list(fields.values()) + [task_id]
        conn.execute("UPDATE tasks SET {0} WHERE id = ?".format(assignments), values)

    def delete_task_row(self, conn: sqlite3.Connection, task_id: str) -> None:
        conn.execute("DELETE FROM tasks WHERE id = ?", (task_id,))

    def list_tasks(
        self,
        conn: sqlite3.Connection,
        include_deleted: bool = False,
        status_filter: str = "all",
        updated_after: Optional[int] = None,
    ) -> List[Dict[str, Any]]:
        """列表查询。

        status_filter: all / active / done
        updated_after: 仅返回 updated_at 严格大于该值的任务（sync 增量）
        排序：sync 增量按 updated_at 升序；普通列表按 due_at 升序（NULL 最后）、created_at 升序。
        """
        where = []
        params: List[Any] = []

        if not include_deleted:
            where.append("deleted = 0")
        if status_filter == "active":
            where.append("done = 0")
        elif status_filter == "done":
            where.append("done = 1")
        if updated_after is not None:
            where.append("updated_at > ?")
            params.append(int(updated_after))

        sql = "SELECT * FROM tasks"
        if where:
            sql += " WHERE " + " AND ".join(where)

        if updated_after is not None:
            sql += " ORDER BY updated_at ASC, id ASC"
        else:
            sql += " ORDER BY (due_at IS NULL) ASC, due_at ASC, created_at ASC, id ASC"

        rows = conn.execute(sql, params).fetchall()
        return [dict(row) for row in rows]

    def count_tasks(self, conn: sqlite3.Connection, include_deleted: bool = False) -> int:
        sql = "SELECT COUNT(*) AS n FROM tasks"
        if not include_deleted:
            sql += " WHERE deleted = 0"
        row = conn.execute(sql).fetchone()
        return int(row["n"]) if row is not None else 0

    def stats_counts(
        self,
        conn: sqlite3.Connection,
        day_start: int,
        tomorrow_start: int,
        week_end: int,
    ) -> Dict[str, int]:
        """统计（全部排除 deleted）。overdue 以“今天 0 点”为界。"""
        row = conn.execute(
            """
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN done = 0 THEN 1 ELSE 0 END), 0) AS active,
                COALESCE(SUM(CASE WHEN done = 1 THEN 1 ELSE 0 END), 0) AS done,
                COALESCE(SUM(CASE WHEN done = 0 AND due_at IS NOT NULL AND due_at < ?
                                  THEN 1 ELSE 0 END), 0) AS overdue,
                COALESCE(SUM(CASE WHEN done = 0 AND due_at IS NOT NULL
                                   AND due_at >= ? AND due_at < ?
                                  THEN 1 ELSE 0 END), 0) AS today,
                COALESCE(SUM(CASE WHEN done = 0 AND due_at IS NOT NULL
                                   AND due_at >= ? AND due_at < ?
                                  THEN 1 ELSE 0 END), 0) AS week
            FROM tasks
            WHERE deleted = 0
            """,
            (day_start, day_start, tomorrow_start, day_start, week_end),
        ).fetchone()
        if row is None:
            return {"total": 0, "active": 0, "done": 0, "overdue": 0, "today": 0, "week": 0}
        return {
            "total": int(row["total"]),
            "active": int(row["active"]),
            "done": int(row["done"]),
            "overdue": int(row["overdue"]),
            "today": int(row["today"]),
            "week": int(row["week"]),
        }

    # ---------------------------------------------------------------- devices

    def touch_device(
        self,
        conn: sqlite3.Connection,
        device_id: str,
        last_seen: int,
        platform: str = "",
    ) -> None:
        conn.execute(
            """
            INSERT INTO devices (id, last_seen, platform) VALUES (?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                last_seen = excluded.last_seen,
                platform = CASE
                    WHEN excluded.platform IS NULL OR excluded.platform = ''
                        THEN devices.platform
                    ELSE excluded.platform
                END
            """,
            (device_id, int(last_seen), platform or ""),
        )

    def count_devices(self, conn: sqlite3.Connection) -> int:
        row = conn.execute("SELECT COUNT(*) AS n FROM devices").fetchone()
        return int(row["n"]) if row is not None else 0

    def list_devices(self, conn: sqlite3.Connection) -> List[Dict[str, Any]]:
        rows = conn.execute("SELECT * FROM devices ORDER BY last_seen DESC").fetchall()
        return [dict(row) for row in rows]


__all__ = ["Store", "SCHEMA_SQL", "TASK_COLUMNS"]
"""业务层离线测试（不依赖 fastapi / 网络）。"""

import os
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

from service import (  # noqa: E402
    NotFoundError,
    TaskService,
    ValidationError,
    local_day_start_ms,
)
from store import Store  # noqa: E402

DAY_MS = 24 * 60 * 60 * 1000
FIXED_NOW = int(datetime(2026, 10, 28, 15, 0, 0).timestamp() * 1000)
TODAY_START = local_day_start_ms(FIXED_NOW)


class FakeClock:
    def __init__(self, value):
        self.value = value

    def __call__(self):
        return self.value

    def advance(self, ms):
        self.value += ms


def sync_task(task_id, title, updated_at, **overrides):
    data = {
        "id": task_id,
        "title": title,
        "notes": "",
        "due_at": None,
        "remind_at": None,
        "priority": 2,
        "done": False,
        "deleted": False,
        "updated_at": updated_at,
    }
    data.update(overrides)
    return data


class ServiceTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.clock = FakeClock(FIXED_NOW)
        self.store = Store(os.path.join(self.tmp.name, "taskmate-test.db"))
        self.svc = TaskService(self.store, clock=self.clock)

    def make_task(self, title, due_at=None, done=False, deleted=False):
        task = self.svc.create_task({"title": title, "due_at": due_at})
        if done or deleted:
            self.clock.advance(1000)
            fields = {}
            if done:
                fields["done"] = True
            if deleted:
                fields["deleted"] = True
            task = self.svc.patch_task(task["id"], fields)
        return task


class CreatePatchDeleteTest(ServiceTestCase):
    def test_create_defaults(self):
        task = self.svc.create_task({"title": "  买牛奶  "})
        self.assertEqual(task["title"], "买牛奶")
        self.assertEqual(task["notes"], "")
        self.assertIsNone(task["due_at"])
        self.assertIsNone(task["remind_at"])
        self.assertEqual(task["priority"], 2)
        self.assertFalse(task["done"])
        self.assertFalse(task["deleted"])
        self.assertEqual(task["updated_at"], FIXED_NOW)
        self.assertNotIn("created_at", task)

    def test_create_with_fields(self):
        due = FIXED_NOW + 3600 * 1000
        task = self.svc.create_task(
            {
                "title": "交周报",
                "notes": "附上本周数据",
                "due_at": due,
                "remind_at": due - 600000,
                "priority": 3,
            }
        )
        self.assertEqual(task["due_at"], due)
        self.assertEqual(task["remind_at"], due - 600000)
        self.assertEqual(task["priority"], 3)

    def test_create_empty_title(self):
        with self.assertRaises(ValidationError):
            self.svc.create_task({"title": "   "})
        with self.assertRaises(ValidationError):
            self.svc.create_task({})

    def test_priority_is_clamped(self):
        self.assertEqual(self.svc.create_task({"title": "a", "priority": 99})["priority"], 3)
        self.assertEqual(self.svc.create_task({"title": "b", "priority": 0})["priority"], 1)

    def test_patch_updates_timestamp_and_fields(self):
        task = self.make_task("取快递")
        self.clock.advance(5000)
        patched = self.svc.patch_task(task["id"], {"title": "取两个快递", "done": True})
        self.assertEqual(patched["title"], "取两个快递")
        self.assertTrue(patched["done"])
        self.assertEqual(patched["updated_at"], FIXED_NOW + 5000)
        # created_at 不因 PATCH 变化，因此列表排序仍稳定
        self.assertEqual(patched["id"], task["id"])

    def test_patch_can_restore_deleted(self):
        task = self.make_task("可恢复")
        self.svc.delete_task(task["id"])
        restored = self.svc.patch_task(task["id"], {"deleted": False})
        self.assertFalse(restored["deleted"])

    def test_patch_missing_task(self):
        with self.assertRaises(NotFoundError):
            self.svc.patch_task("does-not-exist", {"title": "x"})

    def test_patch_bad_type(self):
        task = self.make_task("类型")
        with self.assertRaises(ValidationError):
            self.svc.patch_task(task["id"], {"priority": "high"})

    def test_delete_soft(self):
        task = self.make_task("要删除的")
        self.clock.advance(2000)
        self.svc.delete_task(task["id"])

        visible = self.svc.list_tasks(include_deleted=False)
        self.assertEqual(visible, [])

        everything = self.svc.list_tasks(include_deleted=True)
        self.assertEqual(len(everything), 1)
        self.assertTrue(everything[0]["deleted"])
        self.assertEqual(everything[0]["updated_at"], FIXED_NOW + 2000)

    def test_delete_missing(self):
        with self.assertRaises(NotFoundError):
            self.svc.delete_task("nope")


class SyncTest(ServiceTestCase):
    def test_sync_insert_and_full_pull(self):
        result = self.svc.sync(
            "device-a",
            0,
            [sync_task("t1", "来自手机", 1000, due_at=TODAY_START)],
        )
        self.assertTrue(result["ok"])
        self.assertEqual(result["now"], FIXED_NOW)
        self.assertEqual(len(result["tasks"]), 1)
        self.assertEqual(result["tasks"][0]["id"], "t1")
        self.assertFalse(result["tasks"][0]["done"])

        with self.store.transaction() as conn:
            devices = self.store.list_devices(conn)
        self.assertEqual(len(devices), 1)
        self.assertEqual(devices[0]["id"], "device-a")
        self.assertEqual(devices[0]["last_seen"], FIXED_NOW)

    def test_sync_newer_wins(self):
        self.svc.sync("d", 0, [sync_task("t1", "旧标题", 1000)])
        result = self.svc.sync("d", 0, [sync_task("t1", "新标题", 2000, notes="补充")])
        self.assertEqual(len(result["tasks"]), 1)
        self.assertEqual(result["tasks"][0]["title"], "新标题")
        self.assertEqual(result["tasks"][0]["notes"], "补充")
        self.assertEqual(result["tasks"][0]["updated_at"], 2000)

    def test_sync_older_is_ignored(self):
        self.svc.sync("d", 0, [sync_task("t1", "服务端版本", 2000)])
        result = self.svc.sync("d", 0, [sync_task("t1", "过期版本", 1000)])
        self.assertEqual(result["tasks"][0]["title"], "服务端版本")
        self.assertEqual(result["tasks"][0]["updated_at"], 2000)

    def test_sync_equal_timestamp_is_ignored(self):
        self.svc.sync("d", 0, [sync_task("t1", "先到", 2000)])
        result = self.svc.sync("d", 0, [sync_task("t1", "后到", 2000)])
        self.assertEqual(result["tasks"][0]["title"], "先到")

    def test_sync_tombstone(self):
        self.svc.sync("d", 0, [sync_task("t1", "墓碑", 1000)])
        result = self.svc.sync("d", 0, [sync_task("t1", "墓碑", 2000, deleted=True)])
        self.assertEqual(len(result["tasks"]), 1)
        self.assertTrue(result["tasks"][0]["deleted"])
        self.assertEqual(self.svc.list_tasks(include_deleted=False), [])
        self.assertTrue(self.svc.list_tasks(include_deleted=True)[0]["deleted"])

    def test_sync_since_is_incremental_and_sorted(self):
        self.svc.sync(
            "d",
            0,
            [sync_task("old", "旧", 1000), sync_task("new", "新", 3000)],
        )
        result = self.svc.sync("d", 2000, [])
        ids = [task["id"] for task in result["tasks"]]
        self.assertEqual(ids, ["new"])

        full = self.svc.sync("d", 0, [])
        self.assertEqual([task["id"] for task in full["tasks"]], ["old", "new"])

    def test_sync_validation(self):
        with self.assertRaises(ValidationError):
            self.svc.sync("", 0, [])
        with self.assertRaises(ValidationError):
            self.svc.sync("d", 0, "not-a-list")
        with self.assertRaises(ValidationError):
            self.svc.sync("d", 0, [{"id": "t1", "title": "缺 updated_at"}])
        with self.assertRaises(ValidationError):
            self.svc.sync("d", 0, [sync_task("", "空 id", 1000)])

    def test_sync_all_or_nothing_on_validation_error(self):
        with self.assertRaises(ValidationError):
            self.svc.sync(
                "d",
                0,
                [sync_task("ok", "合法", 1000), {"id": "bad"}],
            )
        self.assertEqual(self.svc.list_tasks(include_deleted=True), [])


class QueryStatsTest(ServiceTestCase):
    def test_list_orders_by_due_then_created(self):
        late = self.make_task("晚", due_at=TODAY_START + 10 * 3600 * 1000)
        early = self.make_task("早", due_at=TODAY_START + 2 * 3600 * 1000)
        none = self.make_task("无日期")
        tasks = self.svc.list_tasks()
        self.assertEqual([t["id"] for t in tasks], [early["id"], late["id"], none["id"]])

    def test_filter_active_done(self):
        active = self.make_task("待办")
        done = self.make_task("完成", done=True)
        self.assertEqual([t["id"] for t in self.svc.list_tasks(status_filter="active")], [active["id"]])
        self.assertEqual([t["id"] for t in self.svc.list_tasks(status_filter="done")], [done["id"]])
        with self.assertRaises(ValidationError):
            self.svc.list_tasks(status_filter="overdue")

    def test_stats(self):
        self.make_task("逾期", due_at=FIXED_NOW - DAY_MS)
        self.make_task("今天", due_at=FIXED_NOW + 3600 * 1000)
        self.make_task("本周", due_at=FIXED_NOW + 3 * DAY_MS)
        self.make_task("以后", due_at=FIXED_NOW + 10 * DAY_MS)
        self.make_task("无日期")
        self.make_task("今天已完成", due_at=FIXED_NOW + 1800 * 1000, done=True)
        deleted = self.make_task("已删除", due_at=FIXED_NOW + 1800 * 1000)
        self.svc.delete_task(deleted["id"])

        stats = self.svc.stats()
        self.assertEqual(stats["ok"], True)
        self.assertEqual(stats["total"], 6)
        self.assertEqual(stats["active"], 5)
        self.assertEqual(stats["done"], 1)
        self.assertEqual(stats["overdue"], 1)
        self.assertEqual(stats["today"], 1)
        self.assertEqual(stats["week"], 2)

    def test_stats_boundaries(self):
        # 今天 0 点整属于 today，不属于 overdue
        self.make_task("零点", due_at=TODAY_START)
        # 明天 0 点整属于 week，不属于 today
        self.make_task("明天零点", due_at=TODAY_START + DAY_MS)
        # 第 7 天 0 点整不在 week 区间内
        self.make_task("第七天", due_at=TODAY_START + 7 * DAY_MS)
        stats = self.svc.stats()
        self.assertEqual(stats["overdue"], 0)
        self.assertEqual(stats["today"], 1)
        self.assertEqual(stats["week"], 2)

    def test_health_counts(self):
        self.assertEqual(self.svc.health(), {"ok": True, "tasks": 0, "devices": 0})
        self.make_task("一条")
        self.svc.sync("phone", 0, [])
        self.assertEqual(self.svc.health(), {"ok": True, "tasks": 1, "devices": 1})


if __name__ == "__main__":
    unittest.main()

class RepeatRuleTest(ServiceTestCase):
    """重复规则：新增、PATCH、同步都要保留。"""

    def test_create_with_repeat(self):
        task = self.svc.create_task({"title": "晨跑", "due_at": FIXED_NOW, "repeat_rule": "daily"})
        self.assertEqual(task["repeat_rule"], "daily")

    def test_create_defaults_to_empty(self):
        task = self.svc.create_task({"title": "买牛奶"})
        self.assertEqual(task["repeat_rule"], "")

    def test_invalid_repeat_falls_back(self):
        task = self.svc.create_task({"title": "写周报", "repeat_rule": "every-second-tuesday"})
        self.assertEqual(task["repeat_rule"], "")

    def test_patch_repeat(self):
        task = self.svc.create_task({"title": "交周报"})
        patched = self.svc.patch_task(task["id"], {"repeat_rule": "weekly"})
        self.assertEqual(patched["repeat_rule"], "weekly")

    def test_patch_clears_repeat(self):
        task = self.svc.create_task({"title": "例会", "repeat_rule": "weekdays"})
        patched = self.svc.patch_task(task["id"], {"repeat_rule": ""})
        self.assertEqual(patched["repeat_rule"], "")

    def test_sync_keeps_repeat(self):
        item = sync_task("id-repeat", "吃药", 1_000, repeat_rule="daily")
        result = self.svc.sync(device_id="dev-1", since=0, tasks=[item])
        stored = [t for t in result["tasks"] if t["id"] == "id-repeat"][0]
        self.assertEqual(stored["repeat_rule"], "daily")

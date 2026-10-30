"""HTTP 层测试：装了 fastapi + httpx 才运行，否则整体跳过。"""

import os
import sys
import tempfile
import unittest
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parents[1]
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

try:
    from fastapi.testclient import TestClient  # noqa: F401

    from app import create_app
    from config import Settings

    FASTAPI_AVAILABLE = True
    IMPORT_ERROR = None
except Exception as exc:  # pragma: no cover - 取决于本机环境
    FASTAPI_AVAILABLE = False
    IMPORT_ERROR = exc


HTTP_SKIP_REASON = "未安装 fastapi/httpx，跳过 HTTP 层测试（业务层测试不受影响）：{0}".format(IMPORT_ERROR)


@unittest.skipUnless(FASTAPI_AVAILABLE, HTTP_SKIP_REASON)
class HttpApiTest(unittest.TestCase):
    API_KEY = "test-key"
    AUTH = {"X-API-Key": "test-key"}

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.db_path = os.path.join(self.tmp.name, "http-test.db")
        self.app = create_app(
            Settings(
                api_key=self.API_KEY,
                db_path=self.db_path,
                static_dir=str(SERVER_DIR / "static"),
            )
        )
        self.client = TestClient(self.app)

    # ------------------------------------------------------------------ 鉴权

    def test_healthz_is_public(self):
        response = self.client.get("/healthz")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"ok": True, "tasks": 0, "devices": 0})

    def test_api_requires_key(self):
        response = self.client.get("/api/tasks")
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json(), {"ok": False, "error": "unauthorized"})

    def test_api_rejects_wrong_key(self):
        response = self.client.get("/api/tasks", headers={"X-API-Key": "wrong"})
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["error"], "unauthorized")

    def test_api_accepts_key(self):
        response = self.client.get("/api/tasks", headers=self.AUTH)
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json()["ok"])

    def test_unknown_api_path_is_guarded(self):
        self.assertEqual(self.client.get("/api/does-not-exist").status_code, 401)
        self.assertEqual(
            self.client.get("/api/does-not-exist", headers=self.AUTH).status_code, 404
        )

    # ------------------------------------------------------------------ 页面

    def test_dashboard_html(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, 200)
        self.assertIn("text/html", response.headers["content-type"])
        self.assertIn("任务助理 · 控制台", response.text)
        self.assertIn("还没有任务，在上面添加一条", response.text)
        self.assertNotIn("cdn.", response.text.lower())

    # -------------------------------------------------------------- 任务流程

    def test_task_lifecycle(self):
        created = self.client.post(
            "/api/tasks",
            headers=self.AUTH,
            json={"title": "写周报", "due_at": 1_800_000_000_000, "priority": 3},
        )
        self.assertEqual(created.status_code, 200)
        task = created.json()["task"]
        self.assertEqual(task["title"], "写周报")
        self.assertEqual(task["priority"], 3)
        self.assertFalse(task["done"])
        self.assertFalse(task["deleted"])
        self.assertNotIn("created_at", task)

        patched = self.client.patch(
            "/api/tasks/{0}".format(task["id"]),
            headers=self.AUTH,
            json={"done": True},
        )
        self.assertEqual(patched.status_code, 200)
        self.assertTrue(patched.json()["task"]["done"])
        self.assertGreaterEqual(patched.json()["task"]["updated_at"], task["updated_at"])

        listed = self.client.get("/api/tasks?filter=done", headers=self.AUTH)
        self.assertEqual(len(listed.json()["tasks"]), 1)

        stats = self.client.get("/api/stats", headers=self.AUTH).json()
        self.assertEqual(stats["total"], 1)
        self.assertEqual(stats["done"], 1)

        deleted = self.client.delete("/api/tasks/{0}".format(task["id"]), headers=self.AUTH)
        self.assertEqual(deleted.status_code, 200)
        self.assertEqual(deleted.json(), {"ok": True})

        visible = self.client.get("/api/tasks", headers=self.AUTH).json()
        self.assertEqual(visible["tasks"], [])
        with_deleted = self.client.get(
            "/api/tasks?include_deleted=1", headers=self.AUTH
        ).json()
        self.assertEqual(len(with_deleted["tasks"]), 1)
        self.assertTrue(with_deleted["tasks"][0]["deleted"])

    def test_create_empty_title_400(self):
        response = self.client.post("/api/tasks", headers=self.AUTH, json={"title": "  "})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["ok"], False)
        self.assertIn("标题", response.json()["error"])

    def test_patch_missing_404(self):
        response = self.client.patch(
            "/api/tasks/not-found", headers=self.AUTH, json={"done": True}
        )
        self.assertEqual(response.status_code, 404)
        self.assertEqual(response.json(), {"ok": False, "error": "任务不存在"})

    def test_invalid_body_and_filter(self):
        bad_json = self.client.post(
            "/api/tasks",
            headers=dict(self.AUTH, **{"Content-Type": "application/json"}),
            content=b"{not json",
        )
        self.assertEqual(bad_json.status_code, 400)

        bad_filter = self.client.get("/api/tasks?filter=whatever", headers=self.AUTH)
        self.assertEqual(bad_filter.status_code, 400)

    def test_sync_endpoint(self):
        incoming = {
            "id": "11111111-1111-1111-1111-111111111111",
            "title": "手机创建",
            "notes": "",
            "due_at": None,
            "remind_at": None,
            "priority": 2,
            "done": False,
            "deleted": False,
            "updated_at": 1_700_000_000_000,
        }
        first = self.client.post(
            "/api/sync",
            headers=self.AUTH,
            json={"device_id": "pixel-7", "since": 0, "tasks": [incoming]},
        )
        self.assertEqual(first.status_code, 200)
        body = first.json()
        self.assertTrue(body["ok"])
        self.assertIsInstance(body["now"], int)
        self.assertEqual(len(body["tasks"]), 1)
        self.assertEqual(body["tasks"][0]["title"], "手机创建")

        second = self.client.post(
            "/api/sync",
            headers=self.AUTH,
            json={
                "device_id": "pixel-7",
                "since": 0,
                "tasks": [dict(incoming, title="改过的标题", updated_at=1_700_000_001_000)],
            },
        )
        self.assertEqual(second.json()["tasks"][0]["title"], "改过的标题")

        incremental = self.client.post(
            "/api/sync",
            headers=self.AUTH,
            json={"device_id": "pixel-7", "since": 1_700_000_000_500, "tasks": []},
        )
        self.assertEqual(len(incremental.json()["tasks"]), 1)

        health = self.client.get("/healthz").json()
        self.assertEqual(health["devices"], 1)
        self.assertEqual(health["tasks"], 1)

    def test_sync_missing_device_id_400(self):
        response = self.client.post(
            "/api/sync", headers=self.AUTH, json={"since": 0, "tasks": []}
        )
        self.assertEqual(response.status_code, 400)


if __name__ == "__main__":
    unittest.main()
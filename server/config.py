"""运行配置：环境变量、API Key、SQLite 路径、监听地址。

仅依赖标准库；python-dotenv 可用时自动加载同级目录下的 .env。
"""

import logging
import os
import secrets
from dataclasses import dataclass
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DB_RELATIVE = "data/taskmate.db"
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8788

logger = logging.getLogger("taskmate.config")


@dataclass
class Settings:
    """服务运行参数（全部可在创建 app 时显式注入，便于测试）。"""

    api_key: str
    db_path: str
    host: str = DEFAULT_HOST
    port: int = DEFAULT_PORT
    static_dir: str = str(BASE_DIR / "static")
    api_key_generated: bool = False

    @property
    def db_file(self) -> Path:
        return Path(self.db_path)

    @property
    def dashboard_file(self) -> Path:
        return Path(self.static_dir) / "index.html"


def generate_api_key() -> str:
    """生成一次性的随机 API Key（首次启动未配置时使用）。"""
    return "tm_" + secrets.token_urlsafe(24)


def _load_dotenv() -> None:
    try:
        from dotenv import load_dotenv  # 可选依赖
    except ImportError:
        return
    env_file = BASE_DIR / ".env"
    if env_file.is_file():
        load_dotenv(str(env_file))


def _resolve_db_path(raw: str) -> str:
    path = Path(raw).expanduser()
    if not path.is_absolute():
        path = BASE_DIR / path
    return str(path)


def load_settings() -> Settings:
    """从环境变量构建配置。未设置 API_KEY 时随机生成并写日志。"""
    _load_dotenv()

    raw_key = (os.environ.get("API_KEY") or "").strip()
    generated = not raw_key
    api_key = raw_key or generate_api_key()

    db_path = _resolve_db_path((os.environ.get("TASKMATE_DB") or DEFAULT_DB_RELATIVE).strip())
    host = (os.environ.get("HOST") or DEFAULT_HOST).strip() or DEFAULT_HOST

    try:
        port = int((os.environ.get("PORT") or str(DEFAULT_PORT)).strip())
    except (TypeError, ValueError):
        port = DEFAULT_PORT
    if not 0 < port < 65536:
        port = DEFAULT_PORT

    if generated:
        logger.warning("未配置 API_KEY，已随机生成：%s", api_key)
        logger.warning("手机端与网页控制台都需要填写这个 Key；固定使用请在 server/.env 中设置 API_KEY。")

    return Settings(
        api_key=api_key,
        db_path=db_path,
        host=host,
        port=port,
        static_dir=str(BASE_DIR / "static"),
        api_key_generated=generated,
    )
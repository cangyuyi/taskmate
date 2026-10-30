"""启动入口：python3 main.py

会打印 API Key、控制台地址和局域网地址，方便手机端填写。
"""

import logging
import socket

import uvicorn

from app import create_app
from config import load_settings

LOG_FORMAT = "%(asctime)s  %(levelname)-7s %(name)s  %(message)s"


def get_lan_ip() -> str:
    """尽力获取本机局域网 IPv4（不会真的发包）。"""
    sock = None
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("192.168.255.255", 1))
        return sock.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
    finally:
        if sock is not None:
            sock.close()


def main() -> None:
    logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
    settings = load_settings()

    lan_ip = get_lan_ip()
    key_note = "（本次随机生成，重启会变化；建议写入 .env 固定）" if settings.api_key_generated else "（来自 API_KEY 环境变量 / .env）"

    lines = [
        "",
        "  任务助理 · 同步服务",
        "  " + "-" * 52,
        "  API Key : {0}".format(settings.api_key),
        "             " + key_note,
        "  本机控制台: http://127.0.0.1:{0}/".format(settings.port),
        "  局域网地址: http://{0}:{1}/".format(lan_ip, settings.port),
        "  健康检查 : http://127.0.0.1:{0}/healthz".format(settings.port),
        "  数据库   : {0}".format(settings.db_path),
        "  " + "-" * 52,
        "  手机端：同一 Wi-Fi 下填入上面的局域网地址 + API Key",
        "  停止服务：Ctrl+C",
        "",
    ]
    print("\n".join(lines), flush=True)

    application = create_app(settings)
    uvicorn.run(application, host=settings.host, port=settings.port, log_level="info")


if __name__ == "__main__":
    main()
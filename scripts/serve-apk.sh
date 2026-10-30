#!/bin/bash
# 在电脑上开一个临时下载页，手机浏览器直接下载 APK
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${1:-8000}"

IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")

echo "──────────────────────────────────────────────"
echo " 手机浏览器打开： http://$IP:$PORT/TaskMate-1.0.apk"
echo " 或者先打开目录： http://$IP:$PORT/"
echo ""
echo " 前提：手机和电脑连同一个 Wi-Fi"
echo " 换端口：bash scripts/serve-apk.sh 8001"
echo " 停止：Ctrl+C"
echo "──────────────────────────────────────────────"

cd "$ROOT/dist"
exec python3 -m http.server "$PORT" --bind 0.0.0.0
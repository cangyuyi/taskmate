#!/bin/bash
# 让电脑端的同步服务开机自启（launchd），并支持卸载。
#   bash scripts/install-autostart.sh           安装并立刻启动
#   bash scripts/install-autostart.sh --remove  取消自启
#   bash scripts/install-autostart.sh --print   只打印 plist（不落盘）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="$ROOT/server"
PYTHON_BIN="$SERVER_DIR/.venv/bin/python"
LABEL="com.edy.taskmate.server"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG_DIR="$SERVER_DIR/data"

MODE="${1:-install}"

if [ ! -x "$PYTHON_BIN" ]; then
  echo "找不到 $PYTHON_BIN" >&2
  echo "先跑一次：cd server && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
  exit 1
fi

print_plist() {
  cat <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
        <string>$PYTHON_BIN</string>
        <string>$SERVER_DIR/main.py</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$SERVER_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$LOG_DIR/server.log</string>
    <key>StandardErrorPath</key>
    <string>$LOG_DIR/server.log</string>
</dict>
</plist>
PLIST
}

case "$MODE" in
  --print)
    print_plist
    ;;

  --remove)
    launchctl unload "$PLIST" 2>/dev/null || true
    rm -f "$PLIST"
    echo "已取消开机自启"
    ;;

  install|"")
    mkdir -p "$LOG_DIR" "$HOME/Library/LaunchAgents"
    if ! print_plist > "$PLIST" 2>/dev/null; then
      echo "写入 $PLIST 失败（当前环境没有权限）。" >&2
      echo "请在你自己的「终端」App 里运行：" >&2
      echo "  bash $ROOT/scripts/install-autostart.sh" >&2
      exit 1
    fi
    launchctl unload "$PLIST" 2>/dev/null || true
    launchctl load "$PLIST"
    sleep 2

    IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")
    echo "──────────────────────────────────────────────"
    echo " 已设为开机自启（崩溃会自动拉起）"
    echo " 控制台： http://127.0.0.1:8788/?key=taskmate-2026"
    echo " 手机填： http://$IP:8788"
    echo " 日志：   $LOG_DIR/server.log"
    echo " 取消：   bash scripts/install-autostart.sh --remove"
    echo "──────────────────────────────────────────────"
    curl -s -m 4 "http://127.0.0.1:8788/healthz" || echo "（服务还没起来，看看日志）"
    echo
    ;;
esac
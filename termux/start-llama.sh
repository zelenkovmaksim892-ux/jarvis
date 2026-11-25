#!/data/data/com.termux/files/usr/bin/bash
# Starts the local model server only on this device's loopback interface.
set -euo pipefail
JARVIS_DIR="${JARVIS_DIR:-$HOME/.jarvis}"
# shellcheck disable=SC1091
[ ! -f "$JARVIS_DIR/llama.conf" ] || . "$JARVIS_DIR/llama.conf"
: "${PORT:=8080}"
HOST=127.0.0.1

case "$PORT" in ''|*[!0-9]*) echo "Invalid local model port" >&2; exit 1 ;; esac
[ "$PORT" -ge 1024 ] && [ "$PORT" -le 65535 ] || { echo "Invalid local model port" >&2; exit 1; }
SESSION=jarvis-ai

if curl -sf --max-time 1 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
  exit 0
fi

if tmux has-session -t "$SESSION" 2>/dev/null && pgrep -f "llama-supervisor.sh" >/dev/null 2>&1; then
  exit 0
fi

tmux kill-session -t "$SESSION" 2>/dev/null || true
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
tmux new-session -d -s "$SESSION" "bash '$JARVIS_DIR/llama-supervisor.sh'"

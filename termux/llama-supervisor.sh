#!/data/data/com.termux/files/usr/bin/bash
# Supervises llama-server. It is never allowed to listen beyond loopback.
set -euo pipefail
JARVIS_DIR="${JARVIS_DIR:-$HOME/.jarvis}"
# shellcheck disable=SC1091
[ ! -f "$JARVIS_DIR/llama.conf" ] || . "$JARVIS_DIR/llama.conf"
: "${MODEL:=$HOME/jarvis/models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf}"
: "${PORT:=8080}" "${CTX:=4096}" "${THREADS:=4}" "${PARALLEL:=1}" "${CACHE_RAM:=512}"
: "${WATCHDOG_GRACE:=90}" "${WATCHDOG_EVERY:=20}"
HOST=127.0.0.1
LOG="$JARVIS_DIR/llama.log"
mkdir -p "$JARVIS_DIR"

is_uint() {
  case "$1" in ''|*[!0-9]*) return 1 ;; esac
  return 0
}
for value in "$PORT" "$CTX" "$THREADS" "$PARALLEL" "$CACHE_RAM" "$WATCHDOG_GRACE" "$WATCHDOG_EVERY"; do
  is_uint "$value" || { echo "Invalid numeric llama-server configuration" >&2; exit 1; }
done
[ "$PORT" -ge 1024 ] && [ "$PORT" -le 65535 ] || { echo "Invalid port" >&2; exit 1; }
[ "$CTX" -ge 512 ] && [ "$CTX" -le 32768 ] || { echo "Invalid context size" >&2; exit 1; }
[ "$THREADS" -ge 1 ] && [ "$THREADS" -le 16 ] || { echo "Invalid thread count" >&2; exit 1; }
[ "$PARALLEL" -ge 1 ] && [ "$PARALLEL" -le 4 ] || { echo "Invalid parallel count" >&2; exit 1; }
[ "$CACHE_RAM" -ge 0 ] && [ "$CACHE_RAM" -le 2048 ] || { echo "Invalid cache size" >&2; exit 1; }

if ! [[ "${API_KEY:-}" =~ ^[a-fA-F0-9]{64}$ ]]; then
  echo "Missing or invalid local API key. Run install-termux.sh again." >&2
  exit 1
fi
if [ ! -f "$MODEL" ]; then
  echo "Model file not found: $MODEL" >>"$LOG"
  exit 1
fi

HELP="$(llama-server --help 2>&1)"
case "$HELP" in
  *--api-key*) ;;
  *) echo "llama-server has no API-key support. Refusing to start an unauthenticated service." >>"$LOG"; exit 1 ;;
esac

ARGS=(-m "$MODEL" --host 127.0.0.1 --port "$PORT" -c "$CTX" -t "$THREADS" --api-key "$API_KEY" --no-webui)
case "$HELP" in *--parallel*) ARGS+=(-np "$PARALLEL") ;; esac
case "$HELP" in *--cache-ram*) ARGS+=(--cache-ram "$CACHE_RAM") ;; esac

backoff=2
while true; do
  if [ -f "$LOG" ] && [ "$(stat -c %s "$LOG" 2>/dev/null || echo 0)" -gt 2000000 ]; then
    mv -f "$LOG" "$LOG.1"
  fi
  echo "$(date '+%F %T') starting llama-server on 127.0.0.1" >>"$LOG"
  llama-server "${ARGS[@]}" >>"$LOG" 2>&1 &
  pid=$!
  started=$(date +%s)

  (
    sleep "$WATCHDOG_GRACE"
    bad=0
    while kill -0 "$pid" 2>/dev/null; do
      if curl -sf --max-time 3 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
        bad=0
      else
        bad=$((bad + 1))
      fi
      if [ "$bad" -ge 3 ]; then
        echo "$(date '+%F %T') watchdog: server not answering, restarting it" >>"$LOG"
        kill "$pid" 2>/dev/null || true
        break
      fi
      sleep "$WATCHDOG_EVERY"
    done
  ) &
  watchdog=$!

  wait "$pid" || true
  kill "$watchdog" 2>/dev/null || true
  ran=$(($(date +%s) - started))
  echo "$(date '+%F %T') llama-server exited after ${ran}s" >>"$LOG"
  if [ "$ran" -gt 60 ]; then
    backoff=2
  else
    backoff=$((backoff * 2 > 60 ? 60 : backoff * 2))
  fi
  sleep "$backoff"
done

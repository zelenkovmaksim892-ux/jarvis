#!/data/data/com.termux/files/usr/bin/bash
# One-time setup. Existing config and scripts are backed up before changes.
set -euo pipefail

SRC="$(cd "$(dirname "$0")" && pwd)"
DEST="$HOME/.jarvis"
STAMP="$(date +%Y%m%d-%H%M%S)"

backup() {
  if [ -e "$1" ]; then
    cp -p "$1" "$1.bak-$STAMP"
    echo "  backed up $1"
  fi
}

mkdir -p "$DEST" "$HOME/.termux/boot"

echo "1. Installing private server scripts"
for f in llama-supervisor.sh start-llama.sh status.sh; do
  backup "$DEST/$f"
  cp "$SRC/$f" "$DEST/$f"
  chmod 700 "$DEST/$f"
done

echo "2. Securing local model configuration"
if [ ! -f "$DEST/llama.conf" ]; then
  cat >"$DEST/llama.conf" <<CONF
# Local model server settings. Edit only if you understand the security implications.
MODEL="\$HOME/jarvis/models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
HOST=127.0.0.1
PORT=8080
CTX=4096
THREADS=4
PARALLEL=1
CACHE_RAM=512
CONF
fi

if ! grep -Eq '^API_KEY="[a-fA-F0-9]{64}"$' "$DEST/llama.conf"; then
  backup "$DEST/llama.conf"
  NEW_API_KEY="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  if [ "${#NEW_API_KEY}" -ne 64 ]; then
    echo "Could not generate a local API key." >&2
    exit 1
  fi
  sed -i '/^API_KEY=/d' "$DEST/llama.conf"
  printf 'API_KEY="%s"\n' "$NEW_API_KEY" >>"$DEST/llama.conf"
fi

# Do not allow a config edit to expose the model server on Wi-Fi or mobile networks.
if grep -q '^HOST=' "$DEST/llama.conf"; then
  sed -i 's/^HOST=.*/HOST=127.0.0.1/' "$DEST/llama.conf"
else
  printf '\nHOST=127.0.0.1\n' >>"$DEST/llama.conf"
fi
# Arbitrary command-line flags could override the bind address or disable protections.
sed -i '/^EXTRA_ARGS=/d' "$DEST/llama.conf"
chmod 600 "$DEST/llama.conf"

echo "3. Allow Jarvis to request this one Termux script"
PROPS="$HOME/.termux/termux.properties"
if [ -f "$PROPS" ] && grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true' "$PROPS"; then
  echo "  already enabled"
else
  backup "$PROPS"
  if [ -f "$PROPS" ] && grep -Eq '^[[:space:]]*#?[[:space:]]*allow-external-apps' "$PROPS"; then
    sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps.*/allow-external-apps = true/' "$PROPS"
  else
    printf '\nallow-external-apps = true\n' >>"$PROPS"
  fi
  if command -v termux-reload-settings >/dev/null 2>&1; then
    termux-reload-settings || true
  fi
  echo "  enabled"
fi

echo "4. Configure Termux:Boot (open that app once after installing it)"
BOOT="$HOME/.termux/boot/start-jarvis"
backup "$BOOT"
cat >"$BOOT" <<'BOOTSCRIPT'
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
bash "$HOME/.jarvis/start-llama.sh"
BOOTSCRIPT
chmod 700 "$BOOT"

echo "5. Starting the local model server"
bash "$DEST/start-llama.sh"
sleep 8
bash "$DEST/status.sh"

API_KEY="$(sed -n 's/^API_KEY="\([a-fA-F0-9]*\)"$/\1/p' "$DEST/llama.conf")"
cat <<NEXT

Setup finished. Android still requires your approval for:
  a) Jarvis > Permissions > "Run commands in Termux environment".
  b) Battery settings for Jarvis and Termux, if Android stops background work.

Check status with: bash ~/.jarvis/status.sh

Local model API key (paste into the Jarvis local model profile):
$API_KEY

Keep this key private. The model server is restricted to 127.0.0.1.
NEXT

#!/bin/zsh
set -e

KEY_PATH="${NBYS_SSH_KEY:-$HOME/.ssh/id_ed25519_nbys_server}"
SERVER="${NBYS_SSH_SERVER:-root@8.160.183.48}"
LOCAL_PORT="${NBYS_DB_LOCAL_PORT:-5433}"

if lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Database tunnel is already listening on 127.0.0.1:$LOCAL_PORT."
  exit 0
fi

ssh \
  -fNT \
  -i "$KEY_PATH" \
  -o IdentitiesOnly=yes \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -L "127.0.0.1:$LOCAL_PORT:127.0.0.1:5432" \
  "$SERVER"

echo "Database tunnel started on 127.0.0.1:$LOCAL_PORT."

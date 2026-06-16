#!/bin/zsh
set -e

KEY_PATH="${NBYS_SSH_KEY:-$HOME/.ssh/id_ed25519_nbys_server}"
SERVER="${NBYS_SSH_SERVER:-root@8.160.183.48}"
LOCAL_PORT="${NBYS_MYSQL_LOCAL_PORT:-3307}"

if lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "MySQL tunnel is already listening on 127.0.0.1:$LOCAL_PORT."
  exit 0
fi

ssh \
  -fNT \
  -i "$KEY_PATH" \
  -o IdentitiesOnly=yes \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -L "127.0.0.1:$LOCAL_PORT:127.0.0.1:3306" \
  "$SERVER"

echo "MySQL tunnel started on 127.0.0.1:$LOCAL_PORT."

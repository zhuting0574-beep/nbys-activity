#!/usr/bin/env bash
set -euo pipefail

SSH_HOST="${SSH_HOST:-8.160.183.48}"
SSH_USER="${SSH_USER:-root}"
SSH_PORT="${SSH_PORT:-22}"
LOCAL_DB_PORT="${LOCAL_DB_PORT:-13306}"
REMOTE_DB_HOST="${REMOTE_DB_HOST:-127.0.0.1}"
REMOTE_DB_PORT="${REMOTE_DB_PORT:-3306}"

if lsof -nP -iTCP:"${LOCAL_DB_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Local port ${LOCAL_DB_PORT} is already listening."
  echo "If this is an existing SSH tunnel, you can start the backend directly."
  exit 0
fi

echo "Starting MySQL SSH tunnel:"
echo "  127.0.0.1:${LOCAL_DB_PORT} -> ${REMOTE_DB_HOST}:${REMOTE_DB_PORT} via ${SSH_USER}@${SSH_HOST}:${SSH_PORT}"

exec ssh \
  -N \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -L "127.0.0.1:${LOCAL_DB_PORT}:${REMOTE_DB_HOST}:${REMOTE_DB_PORT}" \
  -p "${SSH_PORT}" \
  "${SSH_USER}@${SSH_HOST}"

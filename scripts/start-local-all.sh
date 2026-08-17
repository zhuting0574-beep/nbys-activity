#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${NBYS_RUNTIME_DIR:-/tmp/nbys-local-dev}"
TUNNEL_PID_FILE="$RUNTIME_DIR/ssh-db-tunnel.pid"
TUNNEL_LOG="$RUNTIME_DIR/ssh-db-tunnel.log"

if [[ -f "$ROOT_DIR/.env.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.local"
  set +a
fi

SSH_HOST="${SSH_HOST:-8.160.183.48}"
SSH_USER="${SSH_USER:-root}"
SSH_PORT="${SSH_PORT:-22}"
DB_PORT="${DB_PORT:-13307}"
REMOTE_DB_HOST="${REMOTE_DB_HOST:-127.0.0.1}"
REMOTE_DB_PORT="${REMOTE_DB_PORT:-3306}"

port_pid() {
  lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1 || true
}

wait_for_tunnel() {
  local attempt
  for attempt in $(seq 1 30); do
    [[ -n "$(port_pid "$DB_PORT")" ]] && return 0
    sleep 1
  done
  echo "SSH tunnel did not open port $DB_PORT. See $TUNNEL_LOG." >&2
  return 1
}

start_tunnel() {
  if [[ -n "$(port_pid "$DB_PORT")" ]]; then
    echo "SSH database tunnel is already listening on 127.0.0.1:$DB_PORT."
    return 0
  fi

  if [[ ! -x /usr/bin/expect ]]; then
    echo "Missing /usr/bin/expect. Install Expect or configure SSH key authentication." >&2
    exit 1
  fi

  if [[ -z "${SSH_PASSWORD:-}" ]]; then
    read -r -s -p "SSH password for $SSH_USER@$SSH_HOST: " SSH_PASSWORD
    printf '\n'
  fi

  mkdir -p "$RUNTIME_DIR"
  export SSH_PASSWORD SSH_HOST SSH_USER SSH_PORT DB_PORT REMOTE_DB_HOST REMOTE_DB_PORT
  nohup /usr/bin/expect <<'EXPECT_SCRIPT' >"$TUNNEL_LOG" 2>&1 &
set timeout 20
set password $env(SSH_PASSWORD)
spawn ssh -N \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -L 127.0.0.1:$env(DB_PORT):$env(REMOTE_DB_HOST):$env(REMOTE_DB_PORT) \
  -p $env(SSH_PORT) $env(SSH_USER)@$env(SSH_HOST)
expect {
  -re "Are you sure you want to continue connecting.*" { send "yes\r"; exp_continue }
  -re "password:" { send "$password\r"; exp_continue }
  eof { exit 1 }
  timeout { exit 2 }
}
interact
EXPECT_SCRIPT
  echo $! > "$TUNNEL_PID_FILE"
  wait_for_tunnel
  echo "SSH database tunnel connected: 127.0.0.1:$DB_PORT -> $SSH_HOST:$REMOTE_DB_PORT"
}

start_all() {
  start_tunnel
  DB_PORT="$DB_PORT" "$ROOT_DIR/scripts/local-dev.sh" start
}

stop_all() {
  "$ROOT_DIR/scripts/local-dev.sh" stop || true
  if [[ -f "$TUNNEL_PID_FILE" ]]; then
    local pid
    pid="$(cat "$TUNNEL_PID_FILE")"
    kill "$pid" 2>/dev/null || true
    rm -f "$TUNNEL_PID_FILE"
  fi
  echo "Local project and SSH tunnel stopped."
}

case "${1:-start}" in
  start) start_all ;;
  stop) stop_all ;;
  restart) stop_all; start_all ;;
  status) "$ROOT_DIR/scripts/local-dev.sh" status; lsof -nP -iTCP:"$DB_PORT" -sTCP:LISTEN || true ;;
  *) echo "Usage: $0 {start|stop|restart|status}" >&2; exit 2 ;;
esac

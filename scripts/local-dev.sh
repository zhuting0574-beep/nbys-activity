#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${NBYS_RUNTIME_DIR:-/tmp/nbys-local-dev}"
LAUNCHD_LABEL="com.nbys.activity-center"
JAVA_BIN="${JAVA_BIN:-${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk1.8.0_281.jdk/Contents/Home}/bin/java}"
NPM_BIN="${NPM_BIN:-$ROOT_DIR/.tools/node/bin/npm}"
export PATH="$ROOT_DIR/.tools/node/bin:${JAVA_BIN%/java}:$PATH"

mkdir -p "$RUNTIME_DIR"

if [[ -f "$ROOT_DIR/.env.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.local"
  set +a
fi

export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-13306}"
export UPLOAD_DIR="${UPLOAD_DIR:-$ROOT_DIR/uploads}"
export EUREKA_URL="${EUREKA_URL:-http://127.0.0.1:8761/eureka/}"
export AUTH_TOKEN_SECRET="${AUTH_TOKEN_SECRET:-nbys-local-development-secret}"
export APOLLO_BOOTSTRAP_ENABLED="${APOLLO_BOOTSTRAP_ENABLED:-false}"

services="h5-web admin-web api-gateway public-center user-center activity-center eureka-server"

pid_file() { printf '%s/%s.pid' "$RUNTIME_DIR" "$1"; }
log_file() { printf '%s/%s.log' "$RUNTIME_DIR" "$1"; }

port_pid() {
  lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1 || true
}

wait_for_port() {
  local name="$1" port="$2" attempt
  for attempt in $(seq 1 90); do
    if [[ -n "$(port_pid "$port")" ]]; then
      echo "$name is ready on port $port."
      return 0
    fi
    sleep 1
  done
  echo "$name did not open port $port. See $(log_file "$name")." >&2
  return 1
}

assert_port_free() {
  local name="$1" port="$2" pid
  pid="$(port_pid "$port")"
  if [[ -n "$pid" ]]; then
    echo "Cannot start $name: port $port is already owned by PID $pid." >&2
    echo "Run '$0 status' and stop the external process first." >&2
    return 1
  fi
}

stop_pid() {
  local name="$1" file pid
  file="$(pid_file "$name")"
  [[ -f "$file" ]] || return 0
  pid="$(cat "$file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    for _ in $(seq 1 20); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$file"
}

disable_conflicting_launch_agent() {
  local domain="gui/$(id -u)"
  if launchctl print "$domain/$LAUNCHD_LABEL" >/dev/null 2>&1; then
    echo "Stopping conflicting LaunchAgent $LAUNCHD_LABEL..."
    launchctl bootout "$domain/$LAUNCHD_LABEL"
  fi
  for _ in $(seq 1 20); do
    [[ -z "$(port_pid 8081)" ]] && return 0
    sleep 0.5
  done
  echo "LaunchAgent stopped, but port 8081 is still occupied." >&2
  return 1
}

start_java() {
  local name="$1" port="$2" jar="$3"
  [[ -f "$ROOT_DIR/$jar" ]] || {
    echo "Missing $jar. Build the backend before starting local services." >&2
    return 1
  }
  assert_port_free "$name" "$port"
  nohup "$JAVA_BIN" -jar "$ROOT_DIR/$jar" >"$(log_file "$name")" 2>&1 &
  echo $! >"$(pid_file "$name")"
  wait_for_port "$name" "$port"
  port_pid "$port" >"$(pid_file "$name")"
}

start_web() {
  local name="$1" port="$2" directory="$3"
  assert_port_free "$name" "$port"
  (
    cd "$ROOT_DIR/$directory"
    exec nohup "$NPM_BIN" run dev >"$(log_file "$name")" 2>&1
  ) &
  echo $! >"$(pid_file "$name")"
  wait_for_port "$name" "$port"
  port_pid "$port" >"$(pid_file "$name")"
}

start_all() {
  [[ -x "$JAVA_BIN" ]] || { echo "Java not found: $JAVA_BIN" >&2; exit 1; }
  [[ -x "$NPM_BIN" ]] || { echo "npm not found: $NPM_BIN" >&2; exit 1; }
  [[ -n "$(port_pid "$DB_PORT")" ]] || {
    echo "Database tunnel is not listening on port $DB_PORT." >&2
    echo "Start it first with scripts/start-db-tunnel.sh." >&2
    exit 1
  }

  # Check unrelated ports before touching the persistent activity-center job.
  # This keeps a failed `start` attempt from taking down an otherwise healthy stack.
  assert_port_free eureka-server 8761
  assert_port_free api-gateway 8080
  assert_port_free user-center 8082
  assert_port_free public-center 8083
  assert_port_free admin-web 5173
  assert_port_free h5-web 5174
  disable_conflicting_launch_agent
  start_java eureka-server 8761 backend/eureka-server/target/eureka-server-0.1.0.jar
  start_java activity-center 8081 backend/activity-center/target/activity-center-0.1.0.jar
  start_java user-center 8082 backend/user-center/target/user-center-0.1.0.jar
  start_java public-center 8083 backend/public-center/target/public-center-0.1.0.jar
  start_java api-gateway 8080 backend/api-gateway/target/api-gateway-0.1.0.jar
  start_web admin-web 5173 admin-web
  start_web h5-web 5174 h5-web

  echo "Local project is ready:"
  echo "  H5:    http://127.0.0.1:5174/activity/"
  echo "  Admin: http://127.0.0.1:5173/admin/"
  echo "  API:   http://127.0.0.1:8080"
}

stop_all() {
  for name in $services; do stop_pid "$name"; done
  echo "Managed local services stopped."
}

status_all() {
  local spec name port pid source
  for spec in "eureka-server:8761" "api-gateway:8080" "activity-center:8081" "user-center:8082" "public-center:8083" "admin-web:5173" "h5-web:5174"; do
    name="${spec%%:*}"
    port="${spec##*:}"
    pid="$(port_pid "$port")"
    source="external"
    if [[ -f "$(pid_file "$name")" ]] && [[ "$(cat "$(pid_file "$name")")" == "$pid" ]]; then
      source="managed"
    fi
    if [[ -n "$pid" ]]; then
      printf '%-18s UP   port=%s pid=%s owner=%s\n' "$name" "$port" "$pid" "$source"
    else
      printf '%-18s DOWN port=%s\n' "$name" "$port"
    fi
  done
  if launchctl print "gui/$(id -u)/$LAUNCHD_LABEL" >/dev/null 2>&1; then
    echo "WARNING: conflicting LaunchAgent is loaded: $LAUNCHD_LABEL"
  fi
}

case "${1:-}" in
  start) start_all ;;
  stop) stop_all ;;
  restart) stop_all; start_all ;;
  status) status_all ;;
  *) echo "Usage: $0 {start|stop|restart|status}" >&2; exit 2 ;;
esac

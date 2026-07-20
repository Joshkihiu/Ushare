#!/usr/bin/env bash
# stop-supermarket.sh — stops the Python bridge (5005) and Node backend (4000)
# started by launch-supermarket.sh.

set -uo pipefail

stop_port() {
  local port="$1"
  local name="$2"
  local pid
  pid=$(lsof -ti ":$port" -sTCP:LISTEN 2>/dev/null || true)
  if [ -n "$pid" ]; then
    echo "Stopping $name (port $port, pid $pid)..."
    kill -9 "$pid" 2>/dev/null || true
  else
    echo "$name (port $port) wasn't running."
  fi
}

stop_port 5005 "Python bridge"
stop_port 4000 "Node backend"

command -v notify-send >/dev/null 2>&1 && notify-send "Supermarket Terminal" "Stopped."
echo "Done."

#!/usr/bin/env bash
# launch-supermarket.sh
#
# Starts everything the till terminal needs, idempotently — safe to run
# repeatedly (e.g. every time the "supermarket" trigger fires); it checks
# whether each service is already up before starting another copy.

set -uo pipefail

PROJECT_DIR="$HOME/Desktop/TheCompany/supermarket-terminal"
LOG_DIR="$PROJECT_DIR/.launch-logs"
mkdir -p "$LOG_DIR"

notify() {
  command -v notify-send >/dev/null 2>&1 && notify-send "Supermarket Terminal" "$1" >/dev/null 2>&1
  echo "[supermarket-launch] $1"
}

port_is_listening() {
  lsof -i ":$1" -sTCP:LISTEN >/dev/null 2>&1
}

# --- 1. Python ultrasonic bridge (port 5005) ---
if port_is_listening 5005; then
  notify "Python bridge already running."
else
  notify "Starting Python bridge..."
  (
    cd "$PROJECT_DIR/python_bridge" || exit 1
    nohup python3 transceiver_server.py >> "$LOG_DIR/python_bridge.log" 2>&1 &
    disown
  )
fi

# --- 2. Node backend (port 4000) ---
if port_is_listening 4000; then
  notify "Node backend already running."
else
  notify "Starting Node backend..."
  (
    cd "$PROJECT_DIR" || exit 1
    nohup npm start >> "$LOG_DIR/node_backend.log" 2>&1 &
    disown
  )
fi

# --- 3. Wait for Node to actually come up (max ~10s), then open the browser ---
for _ in $(seq 1 20); do
  port_is_listening 4000 && break
  sleep 0.5
done

notify "Opening terminal UI..."
xdg-open "http://localhost:4000" >/dev/null 2>&1 &

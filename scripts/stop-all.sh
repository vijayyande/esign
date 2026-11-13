#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || -n "${WINDIR:-}" ]]; then
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ROOT/scripts/stop-all.ps1"
  exit $?
fi

stopped=0
for port in 8083 8084 8082 8081; do
  pids=""
  if command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  elif command -v fuser >/dev/null 2>&1; then
    pids="$(fuser -n tcp "$port" 2>/dev/null || true)"
  fi
  for pid in $pids; do
    kill "$pid" 2>/dev/null || true
    echo "Stopped pid $pid on port $port."
    stopped=1
  done
done

if [[ -d "$ROOT/logs" ]]; then
  for pidfile in "$ROOT/logs"/*.pid; do
    [[ -e "$pidfile" ]] || continue
    pid="$(cat "$pidfile")"
    if kill "$pid" 2>/dev/null; then
      echo "Stopped pid $pid from $(basename "$pidfile")."
      stopped=1
    fi
    rm -f "$pidfile"
  done
fi

if [[ "$stopped" -eq 0 ]]; then
  echo "No eSign services are listening on ports 8081-8084."
fi

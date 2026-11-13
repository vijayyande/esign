#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || -n "${WINDIR:-}" ]]; then
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ROOT/scripts/start-all.ps1"
  exit $?
fi

cd "$ROOT"
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME is not set." >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven was not found." >&2
  exit 1
fi

mvn -N install
mvn -pl common install -DskipTests

mkdir -p "$ROOT/logs"
start_module() {
  local name="$1"
  local port="$2"
  mvn -f "$name/pom.xml" spring-boot:run >"$ROOT/logs/$name.log" 2>&1 &
  echo $! >"$ROOT/logs/$name.pid"
  echo "Started $name on port $port (pid $!). Log: logs/$name.log"
}

start_module ekyc 8083
start_module ca 8084
start_module esp 8082
start_module asp 8081
echo "Stop them with scripts/stop-all.sh."

#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PORT=${AEGIS_SECURITY_SMOKE_PORT:-$(python3 - <<'PYP'
import socket
s=socket.socket(); s.bind(('127.0.0.1',0)); print(s.getsockname()[1]); s.close()
PYP
)}
TOKEN='aegis-security-smoke-token'
HASH=$(printf '%s' "$TOKEN" | sha256sum | awk '{print $1}')
LOG="$ROOT/.build/security-smoke.log"
PID_FILE="$ROOT/.build/security-smoke.pid"
cleanup(){
  if [ -f "$PID_FILE" ]; then kill "$(cat "$PID_FILE")" >/dev/null 2>&1 || true; rm -f "$PID_FILE"; fi
}
trap cleanup EXIT
AEGIS_PORT="$PORT" AEGIS_PUBLIC_MODE=true AEGIS_AUTH_MODE=api-key AEGIS_API_KEY_SHA256="$HASH" AEGIS_API_KEY_SCOPES='system:read' \
  java --add-modules jdk.httpserver -jar "$ROOT/dist/aegis-server.jar" >"$LOG" 2>&1 &
echo $! > "$PID_FILE"
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$PORT/readiness" >/dev/null 2>&1; then break; fi
  sleep 0.1
done
code=$(curl -sS -o "$ROOT/.build/unauthorized.json" -w '%{http_code}' "http://127.0.0.1:$PORT/api/v1/system/health")
[ "$code" = 401 ] || { echo "expected 401 without token, got $code"; exit 1; }
code=$(curl -sS -o "$ROOT/.build/missing-tenant.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:$PORT/api/v1/system/health")
[ "$code" = 401 ] || { echo "expected 401 without tenant, got $code"; exit 1; }
code=$(curl -sS -o "$ROOT/.build/authorized.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H 'X-Aegis-Tenant: tenant-smoke' "http://127.0.0.1:$PORT/api/v1/system/health")
[ "$code" = 200 ] || { echo "expected 200 with valid auth, got $code"; exit 1; }
cleanup
trap - EXIT
AEGIS_PORT="$PORT" AEGIS_PUBLIC_MODE=true java --add-modules jdk.httpserver -jar "$ROOT/dist/aegis-server.jar" >"$LOG.invalid" 2>&1 &
echo $! > "$PID_FILE"
trap cleanup EXIT
for _ in $(seq 1 30); do
  code=$(curl -sS -o "$ROOT/.build/not-ready.json" -w '%{http_code}' "http://127.0.0.1:$PORT/readiness" 2>/dev/null || true)
  if [ "$code" = 503 ]; then break; fi
  sleep 0.1
done
[ "$code" = 503 ] || { echo "expected 503 with invalid public auth config, got $code"; exit 1; }
echo 'Public security smoke PASS'

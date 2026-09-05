#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
"$ROOT/scripts/build.sh"
mkdir -p "$ROOT/dist"
rm -f "$ROOT/dist/aegis-server.jar" "$ROOT/dist/aegis-cli.jar"
jar --create --file "$ROOT/dist/aegis-server.jar" --main-class io.aegis.server.AegisServer -C "$ROOT/.build/classes" .
jar --create --file "$ROOT/dist/aegis-cli.jar" --main-class io.aegis.cli.AegisCli -C "$ROOT/.build/classes" .
SERVER_SHA=$(sha256sum "$ROOT/dist/aegis-server.jar" | awk '{print $1}')
CLI_SHA=$(sha256sum "$ROOT/dist/aegis-cli.jar" | awk '{print $1}')
MIGRATION_SHA=$(sha256sum "$ROOT/migrations/postgres/V001__canonical_runtime.sql" | awk '{print $1}')
cat > "$ROOT/dist/release-manifest.json" <<JSON
{"product":"AEGIS-CLI","version":"0.3.0-rc3","source_revision":"UNVERSIONED_WORKSPACE","server_sha256":"$SERVER_SHA","cli_sha256":"$CLI_SHA","migration_v001_sha256":"$MIGRATION_SHA","runtime_kernel":"IMPLEMENTED_TESTED","postgres_adapter":"IMPLEMENTED_COMPILE_STATIC_CONTRACT_VERIFIED","postgres_runtime":"NOT_EXECUTED_NO_SERVER","status":"RC3_PUBLIC_BOUNDARY_BASELINE"}
JSON
sha256sum "$ROOT/dist/aegis-server.jar" "$ROOT/dist/aegis-cli.jar" "$ROOT/migrations/postgres/V001__canonical_runtime.sql" > "$ROOT/dist/SHA256SUMS"
echo 'Packaging PASS'

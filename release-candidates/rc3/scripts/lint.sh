#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
rm -rf "$ROOT/.build/lint" && mkdir -p "$ROOT/.build/lint"
mapfile -t sources < <(find "$ROOT/contracts/src/main/java" "$ROOT/core/src/main/java" "$ROOT/application/src/main/java" "$ROOT/adapters/postgres/src/main/java" "$ROOT/server/src/main/java" "$ROOT/cli/src/main/java" -name '*.java' | sort)
javac --add-modules jdk.httpserver -Xlint:all -Werror -d "$ROOT/.build/lint" "${sources[@]}"
if grep -R -nE '^import (org\.springframework|jakarta\.persistence|java\.sql|io\.aegis\.server|io\.aegis\.adapters)' "$ROOT/core/src/main/java" "$ROOT/contracts/src/main/java"; then
  echo 'ARCH-003 dependency violation'; exit 1
fi
if grep -R -nE '^import io\.aegis\.adapters' "$ROOT/application/src/main/java"; then
  echo 'ARCH-004 application -> adapter dependency violation'; exit 1
fi
if grep -R -nE '(password|secret|api[_-]?key)[[:space:]]*=[[:space:]]*["'"'][^"'"']+["'"']' "$ROOT" --include='*.java' --include='*.ts' --include='*.tsx'; then
  echo 'SEC suspicious hardcoded secret detected'; exit 1
fi
echo 'Java lint/architecture PASS'

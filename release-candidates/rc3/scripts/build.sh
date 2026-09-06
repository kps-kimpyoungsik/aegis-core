#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
rm -rf "$ROOT/.build/classes" && mkdir -p "$ROOT/.build/classes"
mapfile -t sources < <(find "$ROOT/contracts/src/main/java" "$ROOT/core/src/main/java" "$ROOT/application/src/main/java" "$ROOT/adapters/postgres/src/main/java" "$ROOT/server/src/main/java" "$ROOT/cli/src/main/java" -name '*.java' | sort)
javac --add-modules jdk.httpserver -Xlint:all -Werror -d "$ROOT/.build/classes" "${sources[@]}"
echo 'Java build PASS'

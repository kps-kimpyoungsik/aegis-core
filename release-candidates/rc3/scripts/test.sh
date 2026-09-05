#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
rm -rf "$ROOT/.build/test" && mkdir -p "$ROOT/.build/test"
mapfile -t main < <(find "$ROOT/contracts/src/main/java" "$ROOT/core/src/main/java" "$ROOT/application/src/main/java" "$ROOT/adapters/postgres/src/main/java" "$ROOT/server/src/main/java" -name '*.java' | sort)
mapfile -t tests < <(find "$ROOT/testkits/src/test/java" -name '*.java' | sort)
javac --add-modules jdk.httpserver -Xlint:all -Werror -d "$ROOT/.build/test" "${main[@]}" "${tests[@]}"
java -cp "$ROOT/.build/test" io.aegis.testkits.ContractSmokeTest
java -cp "$ROOT/.build/test" io.aegis.testkits.RuntimeKernelTest
java -cp "$ROOT/.build/test" io.aegis.testkits.PostgresContractStaticTest
java --add-modules jdk.httpserver -cp "$ROOT/.build/test" io.aegis.testkits.PublicModeSecurityTest

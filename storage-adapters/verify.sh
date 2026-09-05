#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
DIST="$BUILD/dist"
EVIDENCE="$BUILD/evidence"
rm -rf "$BUILD"
mkdir -p "$CLASSES" "$DIST" "$EVIDENCE"
mapfile -t SOURCES < <(find "$ROOT/src/main/java" "$ROOT/src/test/java" -name '*.java' -print | sort)
javac --release 21 -Xlint:all -Werror -d "$CLASSES" "${SOURCES[@]}"
output="$(java -cp "$CLASSES" aegis.storage.StorageAdapterContractTest)"
printf '%s\n' "$output" | tee "$EVIDENCE/storage-adapter-contracts.txt"
pass_line="$(printf '%s\n' "$output" | grep -E '^PASS [0-9]+/[0-9]+$' | tail -n 1 || true)"
[[ -n "$pass_line" ]] || { echo 'EVIDENCE_GUARD_FAIL' >&2; exit 31; }
JAR="$DIST/aegis-storage-live-adapters-0.4.0.jar"
jar --create --file "$JAR" -C "$CLASSES" .
jdeps "$JAR" | tee "$EVIDENCE/jdeps.txt"
sha256sum "$JAR" | tee "$EVIDENCE/sha256.txt"

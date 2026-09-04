#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
DIST="$BUILD/dist"

rm -rf "$BUILD"
mkdir -p "$CLASSES" "$DIST"

mapfile -t SOURCES < <(find "$ROOT/src/main/java" "$ROOT/src/test/java" -name '*.java' -print | sort)

javac --release 21 -Xlint:all -Werror -d "$CLASSES" "${SOURCES[@]}"
java -cp "$CLASSES" aegis.runtime.kernel.RuntimeValidationKernelTest
java -cp "$CLASSES" aegis.runtime.kernel.RuntimeTraceAuditKernelTest

JAR="$DIST/aegis-runtime-kernel-0.2.0.jar"
jar --create --file "$JAR" -C "$CLASSES" .
jdeps "$JAR"
sha256sum "$JAR"

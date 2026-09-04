#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
DIST="$BUILD/dist"

rm -rf "$BUILD"
mkdir -p "$CLASSES" "$DIST"

MAIN="$ROOT/src/main/java/aegis/runtime/kernel/RuntimeValidationKernel.java"
TEST="$ROOT/src/test/java/aegis/runtime/kernel/RuntimeValidationKernelTest.java"

javac -Xlint:all -Werror -d "$CLASSES" "$MAIN" "$TEST"
java -cp "$CLASSES" aegis.runtime.kernel.RuntimeValidationKernelTest
jar --create --file "$DIST/aegis-runtime-kernel-validation-idempotency-0.1.0.jar" -C "$CLASSES" .
jdeps "$DIST/aegis-runtime-kernel-validation-idempotency-0.1.0.jar"
sha256sum "$DIST/aegis-runtime-kernel-validation-idempotency-0.1.0.jar"

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

javac --release 21 -Xlint:all -Werror -d "$OUT" \
  "$ROOT/runtime-kernel/src/main/java/aegis/runtime/kernel/RuntimeExecutionStateKernel.java" \
  "$ROOT/runtime-kernel/src/main/java/aegis/runtime/kernel/RuntimeRecoveryReplayKernel.java" \
  "$ROOT/data-plane/src/main/java/aegis/data/lifecycle/DataLifecycleKernel.java" \
  "$ROOT/integration-gate/src/test/java/aegis/integration/CanonicalRecoveryMigrationIntegrationGateTest.java"

java -cp "$OUT" aegis.integration.CanonicalRecoveryMigrationIntegrationGateTest

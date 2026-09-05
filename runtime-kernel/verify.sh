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

run_and_verify_count() {
  local class_name="$1"; local evidence_file="$2"; local output
  output="$(java -cp "$CLASSES" "$class_name")"
  printf '%s\n' "$output" | tee "$EVIDENCE/$evidence_file"
  local pass_line actual declared
  pass_line="$(printf '%s\n' "$output" | grep -E '^PASS [0-9]+/[0-9]+$' | tail -n 1 || true)"
  [[ -n "$pass_line" ]] || { echo "EVIDENCE_GUARD_FAIL: missing PASS n/n marker for $class_name" >&2; return 31; }
  actual="$(printf '%s' "$pass_line" | sed -E 's/^PASS ([0-9]+)\/([0-9]+)$/\1/')"
  declared="$(printf '%s' "$pass_line" | sed -E 's/^PASS ([0-9]+)\/([0-9]+)$/\2/')"
  [[ "$actual" == "$declared" ]] || { echo "EVIDENCE_GUARD_FAIL: TEST_EVIDENCE_DECLARED_COUNT_DRIFT $class_name actual=$actual declared=$declared" >&2; return 32; }
}

run_and_verify_count aegis.runtime.kernel.RuntimeExecutionStateKernelTest execution-state-count.txt
run_and_verify_count aegis.runtime.kernel.RuntimeDispatchKernelTest dispatch-count.txt
run_and_verify_count aegis.runtime.kernel.RuntimeRecoveryReplayKernelTest recovery-replay-count.txt
run_and_verify_count aegis.runtime.kernel.RuntimeResourceControlKernelTest resource-control-count.txt
run_and_verify_count aegis.runtime.kernel.RuntimeValidationKernelTest validation-count.txt
run_and_verify_count aegis.runtime.kernel.RuntimeTraceAuditKernelTest trace-audit-count.txt
run_and_verify_count aegis.runtime.kernel.RuntimeRollbackKernelTest rollback-count.txt

echo "TEST_EVIDENCE_COUNT_GUARD=PASS" | tee "$EVIDENCE/evidence-count-guard.txt"
JAR="$DIST/aegis-runtime-kernel-0.8.0.jar"
jar --create --file "$JAR" -C "$CLASSES" .
jdeps "$JAR" | tee "$EVIDENCE/jdeps.txt"
sha256sum "$JAR" | tee "$EVIDENCE/sha256.txt"

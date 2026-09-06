#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  local code="$1"
  shift
  printf 'AEGIS-PRECOMMIT-%s %s\n' "$code" "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "010" "MISSING_TOOL tool=$1"
}

require_command git

git fetch origin main --quiet || fail "001" "MAIN_REFRESH_FAILED remote=origin branch=main"
REMOTE_MAIN_SHA="$(git rev-parse origin/main)"
BASE_SHA="${AEGIS_PRECOMMIT_BASE_SHA:-$REMOTE_MAIN_SHA}"
HEAD_SHA="$(git rev-parse HEAD)"

if [[ "$BASE_SHA" != "$REMOTE_MAIN_SHA" ]]; then
  fail "002" "STALE_BASE expected=main@$REMOTE_MAIN_SHA actual=main@$BASE_SHA"
fi

if ! git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null; then
  fail "003" "UNKNOWN_BASE sha=$BASE_SHA"
fi

if ! git merge-base --is-ancestor "$BASE_SHA" "$HEAD_SHA"; then
  fail "004" "UNRECONCILED_HEAD base=$BASE_SHA head=$HEAD_SHA"
fi

mapfile -t CHANGED_PATHS < <(
  {
    git diff --name-only "$BASE_SHA" --
    git ls-files --others --exclude-standard
  } | sed '/^$/d' | sort -u
)

if [[ ${#CHANGED_PATHS[@]} -eq 0 ]]; then
  printf 'AEGIS-PRECOMMIT-PASS base=%s head=%s changed=0\n' "$BASE_SHA" "$HEAD_SHA"
  exit 0
fi

printf 'AEGIS pre-commit impact set (%d paths):\n' "${#CHANGED_PATHS[@]}"
printf ' - %s\n' "${CHANGED_PATHS[@]}"

matches_any_prefix() {
  local path prefix
  for path in "${CHANGED_PATHS[@]}"; do
    for prefix in "$@"; do
      if [[ "$path" == "$prefix" || "$path" == "$prefix"/* ]]; then
        return 0
      fi
    done
  done
  return 1
}

GATE_SELF_CHANGED=false
for path in "${CHANGED_PATHS[@]}"; do
  if [[ "$path" == "scripts/precommit-verify.sh" || "$path" == ".github/workflows/precommit-regression-gate.yml" ]]; then
    GATE_SELF_CHANGED=true
    break
  fi
done

# Repository-wide anti-duplication gate. Reuse the canonical product tools instead
# of creating parallel duplicate/ownership implementations for non-product changes.
require_command node
(
  cd product
  node tools/ownership-check.mjs
  node tools/workstream-collision-check.mjs
  node tools/duplicate-check.mjs
)

if $GATE_SELF_CHANGED || matches_any_prefix product; then
  require_command npm
  (
    cd product
    npm ci --ignore-scripts --no-audit --no-fund
    npm run verify
    AEGIS_PR_BASE_SHA="$BASE_SHA" npm run check:release-evolution
  )
fi

if $GATE_SELF_CHANGED || matches_any_prefix runtime-kernel data-plane storage-adapters integration-gate; then
  require_command java
  require_command javac
  bash runtime-kernel/verify.sh
  bash data-plane/verify.sh
  bash storage-adapters/verify.sh
  bash integration-gate/verify.sh
fi

printf 'AEGIS-PRECOMMIT-PASS base=%s head=%s changed=%d\n' "$BASE_SHA" "$HEAD_SHA" "${#CHANGED_PATHS[@]}"

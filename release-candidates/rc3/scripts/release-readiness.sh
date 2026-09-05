#!/usr/bin/env bash
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
fail=0
warn=0
check_file(){ if [ -f "$1" ]; then echo "PASS file $1"; else echo "FAIL file $1"; fail=$((fail+1)); fi; }

printf 'AEGIS RELEASE READINESS GATE\n'
printf '===========================\n'
check_file dist/aegis-server.jar
check_file dist/aegis-cli.jar
check_file dist/SHA256SUMS
check_file dist/release-manifest.json
check_file migrations/postgres/V001__canonical_runtime.sql
check_file deployment/container/server.Dockerfile
check_file deployment/kubernetes/aegis-server.yaml
check_file web/package.json

./scripts/lint.sh || fail=$((fail+1))
./scripts/test.sh || fail=$((fail+1))

if [ -f web/package-lock.json ]; then
  echo "PASS web dependency lock"
else
  echo "FAIL web/package-lock.json missing: npm ci is non-runnable/reproducibility gate failed"
  fail=$((fail+1))
fi
if grep -q '"latest"' web/package.json; then
  echo "FAIL floating frontend dependency versions detected"
  fail=$((fail+1))
else
  echo "PASS frontend dependencies pinned"
fi

if command -v psql >/dev/null 2>&1; then echo "PASS PostgreSQL client"; else echo "BLOCKED PostgreSQL runtime verification"; fail=$((fail+1)); fi
if command -v docker >/dev/null 2>&1 || command -v podman >/dev/null 2>&1; then echo "PASS OCI runtime"; else echo "BLOCKED OCI image build verification"; fail=$((fail+1)); fi

if [ -f release/POSTGRES_RUNTIME_VERIFIED ]; then echo "PASS PostgreSQL E2E evidence marker"; else echo "FAIL PostgreSQL E2E not verified"; fail=$((fail+1)); fi
if [ -f release/BACKUP_RESTORE_VERIFIED ]; then echo "PASS backup/restore evidence marker"; else echo "FAIL backup/restore not verified"; fail=$((fail+1)); fi
if [ -f release/WEB_BUILD_VERIFIED ]; then echo "PASS React production build evidence marker"; else echo "FAIL React production build not verified"; fail=$((fail+1)); fi
if [ -f release/OCI_IMAGE_VERIFIED ]; then echo "PASS OCI image evidence marker"; else echo "FAIL OCI image not verified"; fail=$((fail+1)); fi
if [ -f release/STAGING_ACCEPTANCE_VERIFIED ]; then echo "PASS staging acceptance marker"; else echo "FAIL staging acceptance not verified"; fail=$((fail+1)); fi
if [ -f release/SECURITY_RELEASE_GATE_VERIFIED ]; then echo "PASS security release marker"; else echo "FAIL security release gate not verified"; fail=$((fail+1)); fi
if [ -f release/AUTH_BOUNDARY_VERIFIED ]; then echo "PASS minimal public auth boundary evidence"; else echo "FAIL public auth boundary not verified"; fail=$((fail+1)); fi
if [ -f release/CUSTOMER_IDENTITY_PROVIDER_VERIFIED ]; then echo "PASS customer identity provider evidence"; else echo "FAIL customer-grade identity provider/OIDC-RBAC not verified"; fail=$((fail+1)); fi

printf '\nBLOCKERS=%d WARNINGS=%d\n' "$fail" "$warn"
if [ "$fail" -eq 0 ]; then
  echo "DECISION=GO"
  exit 0
fi
echo "DECISION=NO_GO"
exit 2

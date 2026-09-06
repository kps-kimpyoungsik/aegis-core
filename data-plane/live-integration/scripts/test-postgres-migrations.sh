#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_PG_HOST:=127.0.0.1}"
: "${AEGIS_PG_PORT:=54329}"
: "${AEGIS_PG_DB:=aegis}"
: "${AEGIS_PG_USER:=aegis}"
: "${AEGIS_POSTGRES_PASSWORD:?AEGIS_POSTGRES_PASSWORD is required}"

export PGPASSWORD="${AEGIS_POSTGRES_PASSWORD}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="${AEGIS_SQL_DIR:-$(cd "${SCRIPT_DIR}/../sql" && pwd)}"
RUNNER="${SCRIPT_DIR}/run-postgres-migrations.sh"
LOCK_KEY="aegis-schema-migrations-v1"

shopt -s nullglob
migrations=("${SQL_DIR}"/*.sql)
shopt -u nullglob
expected_count="${#migrations[@]}"
if ((expected_count == 0)); then echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=NO_MIGRATIONS" >&2; exit 1; fi

psql_scalar() {
  psql -X -Atq -v ON_ERROR_STOP=1 -h "${AEGIS_PG_HOST}" -p "${AEGIS_PG_PORT}" -U "${AEGIS_PG_USER}" -d "${AEGIS_PG_DB}" -c "$1"
}

before_count="$(psql_scalar 'SELECT count(*) FROM aegis_schema_migrations;')"
if [[ "${before_count}" != "${expected_count}" ]]; then echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=LEDGER_COUNT_BEFORE expected=${expected_count} actual=${before_count}" >&2; exit 1; fi

rerun_output="$(bash "${RUNNER}")"
after_count="$(psql_scalar 'SELECT count(*) FROM aegis_schema_migrations;')"
if [[ "${after_count}" != "${expected_count}" ]]; then echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=LEDGER_COUNT_AFTER expected=${expected_count} actual=${after_count}" >&2; exit 1; fi
skip_count="$(grep -c '^POSTGRES_MIGRATION=SKIP ' <<<"${rerun_output}" || true)"
if [[ "${skip_count}" != "${expected_count}" ]]; then echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=SECOND_RUN_NOT_NOOP expected=${expected_count} skips=${skip_count}" >&2; exit 1; fi

tamper_dir="$(mktemp -d)"; lock_ready="$(mktemp)"
cleanup() { rm -rf "${tamper_dir}"; rm -f "${lock_ready}"; }
trap cleanup EXIT
cp "${SQL_DIR}"/*.sql "${tamper_dir}/"
sed -i '$i-- checksum tamper sentinel' "${tamper_dir}/$(basename "${migrations[0]}")"
if AEGIS_SQL_DIR="${tamper_dir}" bash "${RUNNER}" >/tmp/aegis-migration-tamper.log 2>&1; then echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=CHECKSUM_TAMPER_ACCEPTED" >&2; exit 1; fi
grep -q 'AEGIS_MIGRATION_CHECKSUM_MISMATCH' /tmp/aegis-migration-tamper.log || { echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=CHECKSUM_TAMPER_WRONG_FAILURE" >&2; exit 1; }

psql -X -v ON_ERROR_STOP=1 -h "${AEGIS_PG_HOST}" -p "${AEGIS_PG_PORT}" -U "${AEGIS_PG_USER}" -d "${AEGIS_PG_DB}" >/tmp/aegis-migration-lock-holder.log 2>&1 <<SQL &
SELECT pg_advisory_lock(hashtextextended('${LOCK_KEY}', 0));
\\! touch '${lock_ready}'
SELECT pg_sleep(3);
SQL
lock_pid=$!
for _ in $(seq 1 50); do [[ -f "${lock_ready}" ]] && break; sleep 0.1; done
if [[ ! -f "${lock_ready}" ]]; then kill "${lock_pid}" 2>/dev/null || true; wait "${lock_pid}" 2>/dev/null || true; echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=LOCK_HOLDER_NOT_READY" >&2; exit 1; fi
if bash "${RUNNER}" >/tmp/aegis-migration-lock-contender.log 2>&1; then wait "${lock_pid}"; echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=CONCURRENT_WRITER_ACCEPTED" >&2; exit 1; fi
grep -q 'AEGIS_MIGRATION_LOCK_BUSY' /tmp/aegis-migration-lock-contender.log || { wait "${lock_pid}"; echo "POSTGRES_MIGRATION_GUARDS=FAIL reason=CONCURRENT_WRITER_WRONG_FAILURE" >&2; exit 1; }
wait "${lock_pid}"
printf 'POSTGRES_MIGRATION_GUARDS=PASS migrations=%d\n' "${expected_count}"

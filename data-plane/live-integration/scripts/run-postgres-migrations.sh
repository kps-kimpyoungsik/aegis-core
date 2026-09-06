#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_PG_HOST:=127.0.0.1}"
: "${AEGIS_PG_PORT:=54329}"
: "${AEGIS_PG_DB:=aegis}"
: "${AEGIS_PG_USER:=aegis}"
: "${AEGIS_POSTGRES_PASSWORD:?AEGIS_POSTGRES_PASSWORD is required}"

export PGPASSWORD="${AEGIS_POSTGRES_PASSWORD}"
SQL_DIR="${AEGIS_SQL_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../sql" && pwd)}"
LOCK_KEY="aegis-schema-migrations-v1"
EXECUTION_ID="${AEGIS_MIGRATION_EXECUTION_ID:-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}-$$}"

command -v psql >/dev/null 2>&1 || { echo "POSTGRES_MIGRATIONS=FAIL reason=PSQL_NOT_FOUND" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "POSTGRES_MIGRATIONS=FAIL reason=SHA256SUM_NOT_FOUND" >&2; exit 1; }

shopt -s nullglob
migrations=("${SQL_DIR}"/*.sql)
shopt -u nullglob
if ((${#migrations[@]} == 0)); then echo "POSTGRES_MIGRATIONS=FAIL reason=NO_MIGRATIONS" >&2; exit 1; fi
IFS=$'\n' migrations=($(printf '%s\n' "${migrations[@]}" | LC_ALL=C sort)); unset IFS

declare -A seen_versions=()
tmp_sql="$(mktemp)"
cleanup() { rm -f "${tmp_sql}"; }
trap cleanup EXIT
sql_literal() { local value="$1"; printf "%s" "${value//\'/\'\'}"; }

cat >"${tmp_sql}" <<SQL
\\set ON_ERROR_STOP on
DO \$aegis_lock\$
BEGIN
  IF NOT pg_try_advisory_lock(hashtextextended('${LOCK_KEY}', 0)) THEN
    RAISE EXCEPTION 'AEGIS_MIGRATION_LOCK_BUSY';
  END IF;
END
\$aegis_lock\$;

CREATE TABLE IF NOT EXISTS aegis_schema_migrations (
  version TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  execution_id TEXT NOT NULL
);
SQL

for migration in "${migrations[@]}"; do
  filename="$(basename "${migration}")"
  if [[ ! "${filename}" =~ ^([0-9]{3,})_([A-Za-z0-9][A-Za-z0-9._-]*)\.sql$ ]]; then
    echo "POSTGRES_MIGRATIONS=FAIL reason=INVALID_MIGRATION_NAME file=${filename}" >&2; exit 1
  fi
  version="${BASH_REMATCH[1]}"
  if [[ -n "${seen_versions[${version}]:-}" ]]; then echo "POSTGRES_MIGRATIONS=FAIL reason=DUPLICATE_VERSION version=${version}" >&2; exit 1; fi
  seen_versions["${version}"]=1
  first_statement="$(head -n 1 "${migration}" | tr -d '\\r')"
  last_statement="$(tail -n 1 "${migration}" | tr -d '\\r')"
  if [[ "${first_statement}" != "BEGIN;" || "${last_statement}" != "COMMIT;" ]]; then
    echo "POSTGRES_MIGRATIONS=FAIL reason=NON_TRANSACTIONAL_MIGRATION_UNSUPPORTED file=${filename}" >&2; exit 1
  fi
  checksum="$(sha256sum "${migration}" | awk '{print $1}')"
  escaped_version="$(sql_literal "${version}")"; escaped_name="$(sql_literal "${filename}")"; escaped_checksum="$(sql_literal "${checksum}")"; escaped_execution_id="$(sql_literal "${EXECUTION_ID}")"
  cat >>"${tmp_sql}" <<SQL

DO \$aegis_checksum\$
BEGIN
  IF EXISTS (
    SELECT 1 FROM aegis_schema_migrations
    WHERE version = '${escaped_version}'
      AND (name <> '${escaped_name}' OR sha256 <> '${escaped_checksum}')
  ) THEN
    RAISE EXCEPTION 'AEGIS_MIGRATION_CHECKSUM_MISMATCH version=${escaped_version}';
  END IF;
END
\$aegis_checksum\$;

SELECT EXISTS (
  SELECT 1 FROM aegis_schema_migrations
  WHERE version = '${escaped_version}' AND name = '${escaped_name}' AND sha256 = '${escaped_checksum}'
) AS aegis_migration_applied \\gset

\\if :aegis_migration_applied
\\echo 'POSTGRES_MIGRATION=SKIP version=${escaped_version} name=${escaped_name}'
\\else
BEGIN;
SQL
  sed '1{/^[[:space:]]*BEGIN;[[:space:]]*$/d}; $ {/^[[:space:]]*COMMIT;[[:space:]]*$/d;}' "${migration}" >>"${tmp_sql}"
  cat >>"${tmp_sql}" <<SQL
INSERT INTO aegis_schema_migrations(version, name, sha256, execution_id)
VALUES ('${escaped_version}', '${escaped_name}', '${escaped_checksum}', '${escaped_execution_id}');
COMMIT;
\\echo 'POSTGRES_MIGRATION=APPLIED version=${escaped_version} name=${escaped_name}'
\\endif
SQL
done

cat >>"${tmp_sql}" <<SQL
SELECT pg_advisory_unlock(hashtextextended('${LOCK_KEY}', 0));
SQL

psql -X -v ON_ERROR_STOP=1 -h "${AEGIS_PG_HOST}" -p "${AEGIS_PG_PORT}" -U "${AEGIS_PG_USER}" -d "${AEGIS_PG_DB}" -f "${tmp_sql}"
printf 'POSTGRES_MIGRATIONS=PASS count=%d\n' "${#migrations[@]}"

#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_PG_HOST:=127.0.0.1}"
: "${AEGIS_PG_PORT:=54329}"
: "${AEGIS_PG_DB:=aegis}"
: "${AEGIS_PG_USER:=aegis}"
: "${AEGIS_POSTGRES_PASSWORD:?AEGIS_POSTGRES_PASSWORD is required}"

export PGPASSWORD="${AEGIS_POSTGRES_PASSWORD}"
SQL_DIR="${AEGIS_SQL_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../sql" && pwd)}"

shopt -s nullglob
migrations=("${SQL_DIR}"/*.sql)
shopt -u nullglob

if ((${#migrations[@]} == 0)); then
  echo "POSTGRES_MIGRATIONS=FAIL reason=NO_MIGRATIONS" >&2
  exit 1
fi

for migration in "${migrations[@]}"; do
  psql -X -v ON_ERROR_STOP=1 \
    -h "${AEGIS_PG_HOST}" \
    -p "${AEGIS_PG_PORT}" \
    -U "${AEGIS_PG_USER}" \
    -d "${AEGIS_PG_DB}" \
    -f "${migration}"
done

printf 'POSTGRES_MIGRATIONS=PASS count=%d\n' "${#migrations[@]}"

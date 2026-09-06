#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_PG_HOST:=127.0.0.1}"
: "${AEGIS_PG_PORT:=54329}"
: "${AEGIS_PG_DB:=aegis}"
: "${AEGIS_PG_USER:=aegis}"
: "${AEGIS_POSTGRES_PASSWORD:?AEGIS_POSTGRES_PASSWORD is required}"

export PGPASSWORD="${AEGIS_POSTGRES_PASSWORD}"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "${AEGIS_PG_HOST}" -p "${AEGIS_PG_PORT}" -U "${AEGIS_PG_USER}" -d "${AEGIS_PG_DB}")
DUMP_DIR="${RUNNER_TEMP:-/tmp}/aegis-p4-recovery"
DUMP_FILE="${DUMP_DIR}/aegis-data-plane.dump"
mkdir -p "${DUMP_DIR}"
rm -f "${DUMP_FILE}"

scalar() {
  "${PSQL[@]}" -Atc "SET aegis.tenant_id = 'legacy'; $1" | tr -d '[:space:]'
}

hash_table() {
  local table="$1"
  local order_by="$2"
  scalar "SELECT md5(COALESCE(string_agg(row_to_json(t)::text, E'\\n' ORDER BY ${order_by}), '')) FROM ${table} t;"
}

"${PSQL[@]}" <<'SQL'
TRUNCATE aegis_projection, aegis_outbox, aegis_record;
SET aegis.tenant_id = 'legacy';

BEGIN;
INSERT INTO aegis_record(tenant_id, dataset_id, record_id, version, payload)
VALUES ('legacy', 'work', 'record-1', 1, '{"value":"v1"}');
INSERT INTO aegis_outbox(event_id, tenant_id, dataset_id, record_id, record_version, event_type, payload)
VALUES ('event-1', 'legacy', 'work', 'record-1', 1, 'UPSERT', '{"value":"v1"}');
COMMIT;

BEGIN;
UPDATE aegis_record
SET version = 2, payload = '{"value":"v2"}'
WHERE tenant_id = 'legacy' AND dataset_id = 'work' AND record_id = 'record-1' AND version = 1;
INSERT INTO aegis_outbox(event_id, tenant_id, dataset_id, record_id, record_version, event_type, payload)
VALUES ('event-2', 'legacy', 'work', 'record-1', 2, 'UPSERT', '{"value":"v2"}');
COMMIT;

BEGIN;
INSERT INTO aegis_record(tenant_id, dataset_id, record_id, version, payload)
VALUES ('legacy', 'work', 'record-2', 1, '{"value":"sensitive"}');
INSERT INTO aegis_outbox(event_id, tenant_id, dataset_id, record_id, record_version, event_type, payload)
VALUES ('event-3', 'legacy', 'work', 'record-2', 1, 'UPSERT', '{"value":"sensitive"}');
UPDATE aegis_record
SET version = 2, payload = '{"retracted":true}'
WHERE tenant_id = 'legacy' AND dataset_id = 'work' AND record_id = 'record-2' AND version = 1;
INSERT INTO aegis_outbox(event_id, tenant_id, dataset_id, record_id, record_version, event_type, payload)
VALUES ('event-4', 'legacy', 'work', 'record-2', 2, 'RETRACTED', '{"retracted":true}');
COMMIT;

INSERT INTO aegis_projection(tenant_id, projection_id, entity_id, source_version, payload)
VALUES ('legacy', 'work-view', 'record-1', 2, '{"value":"v2"}');
SQL

before_record_hash="$(hash_table aegis_record 'tenant_id, dataset_id, record_id')"
before_outbox_hash="$(hash_table aegis_outbox 'tenant_id, event_id')"
before_projection_hash="$(hash_table aegis_projection 'tenant_id, projection_id, entity_id')"

pg_dump -h "${AEGIS_PG_HOST}" -p "${AEGIS_PG_PORT}" -U "${AEGIS_PG_USER}" -d "${AEGIS_PG_DB}" \
  --format=custom --data-only --no-owner --no-privileges \
  --table=aegis_record --table=aegis_outbox --table=aegis_projection \
  --file="${DUMP_FILE}"

test -s "${DUMP_FILE}"
dump_sha256="$(sha256sum "${DUMP_FILE}" | awk '{print $1}')"
test -n "${dump_sha256}"

"${PSQL[@]}" -c 'TRUNCATE aegis_projection, aegis_outbox, aegis_record;'
[[ "$(scalar 'SELECT count(*) FROM aegis_record;')" == "0" ]]
[[ "$(scalar 'SELECT count(*) FROM aegis_outbox;')" == "0" ]]
[[ "$(scalar 'SELECT count(*) FROM aegis_projection;')" == "0" ]]

pg_restore -h "${AEGIS_PG_HOST}" -p "${AEGIS_PG_PORT}" -U "${AEGIS_PG_USER}" -d "${AEGIS_PG_DB}" \
  --data-only --no-owner --no-privileges --exit-on-error "${DUMP_FILE}"

after_record_hash="$(hash_table aegis_record 'tenant_id, dataset_id, record_id')"
after_outbox_hash="$(hash_table aegis_outbox 'tenant_id, event_id')"
after_projection_hash="$(hash_table aegis_projection 'tenant_id, projection_id, entity_id')"

[[ "${before_record_hash}" == "${after_record_hash}" ]]
[[ "${before_outbox_hash}" == "${after_outbox_hash}" ]]
[[ "${before_projection_hash}" == "${after_projection_hash}" ]]

# Rebuild a derived projection from the canonical outbox. RETRACTED is a tombstone
# and therefore must not resurrect into the active projection.
"${PSQL[@]}" <<'SQL'
SET aegis.tenant_id = 'legacy';
TRUNCATE aegis_projection;
WITH latest AS (
  SELECT DISTINCT ON (tenant_id, dataset_id, record_id)
         tenant_id, dataset_id, record_id, record_version, event_type, payload
  FROM aegis_outbox
  WHERE tenant_id = 'legacy'
  ORDER BY tenant_id, dataset_id, record_id, record_version DESC, event_id DESC
)
INSERT INTO aegis_projection(tenant_id, projection_id, entity_id, source_version, payload)
SELECT tenant_id, dataset_id || '-view', record_id, record_version, payload
FROM latest
WHERE event_type <> 'RETRACTED';
SQL

[[ "$(scalar "SELECT source_version FROM aegis_projection WHERE tenant_id='legacy' AND projection_id='work-view' AND entity_id='record-1';")" == "2" ]]
[[ "$(scalar "SELECT count(*) FROM aegis_projection WHERE tenant_id='legacy' AND projection_id='work-view' AND entity_id='record-2';")" == "0" ]]

# A stale replay must not downgrade a rebuilt projection.
"${PSQL[@]}" <<'SQL'
SET aegis.tenant_id = 'legacy';
INSERT INTO aegis_projection(tenant_id, projection_id, entity_id, source_version, payload)
VALUES ('legacy', 'work-view', 'record-1', 1, '{"value":"v1-stale"}')
ON CONFLICT (tenant_id, projection_id, entity_id) DO UPDATE
SET source_version = EXCLUDED.source_version,
    payload = EXCLUDED.payload
WHERE aegis_projection.source_version < EXCLUDED.source_version;
SQL

[[ "$(scalar "SELECT source_version FROM aegis_projection WHERE tenant_id='legacy' AND projection_id='work-view' AND entity_id='record-1';")" == "2" ]]
[[ "$(scalar "SELECT payload->>'value' FROM aegis_projection WHERE tenant_id='legacy' AND projection_id='work-view' AND entity_id='record-1';")" == "v2" ]]

printf 'P4_RECOVERY_LIVE=PASS\n'
printf 'backup_sha256=%s\n' "${dump_sha256}"
printf 'record_hash=%s\n' "${after_record_hash}"
printf 'outbox_hash=%s\n' "${after_outbox_hash}"
printf 'projection_backup_hash=%s\n' "${after_projection_hash}"
printf 'retracted_projection_count=0\n'
printf 'stale_replay_source_version=2\n'

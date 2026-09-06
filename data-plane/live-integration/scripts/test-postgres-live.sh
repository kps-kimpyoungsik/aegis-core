#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_PG_URL:=postgresql://aegis:${AEGIS_POSTGRES_PASSWORD}@127.0.0.1:54329/aegis}"

psql "$AEGIS_PG_URL" -v ON_ERROR_STOP=1 <<'SQL'
TRUNCATE aegis_outbox, aegis_projection, aegis_record;
SET aegis.tenant_id = 'legacy';

-- Atomic source + outbox boundary.
BEGIN;
INSERT INTO aegis_record(tenant_id,dataset_id, record_id, version, payload)
VALUES ('legacy','runtime.execution','exec-1',1,'{"state":"RUNNING"}');

INSERT INTO aegis_outbox(event_id,tenant_id,dataset_id,record_id,record_version,event_type,payload)
VALUES ('evt-1','legacy','runtime.execution','exec-1',1,'EXECUTION_UPDATED','{"state":"RUNNING"}');
COMMIT;

DO $$
DECLARE rc int; oc int;
BEGIN
  SELECT count(*) INTO rc FROM aegis_record WHERE tenant_id='legacy' AND record_id='exec-1' AND version=1;
  SELECT count(*) INTO oc FROM aegis_outbox WHERE tenant_id='legacy' AND event_id='evt-1' AND record_version=1;
  IF rc <> 1 OR oc <> 1 THEN
    RAISE EXCEPTION 'source/outbox atomicity verification failed';
  END IF;
END $$;

-- Optimistic version guard: first update succeeds, stale update cannot overwrite it.
UPDATE aegis_record
SET version=2, payload='{"state":"COMPLETED"}'
WHERE tenant_id='legacy' AND dataset_id='runtime.execution' AND record_id='exec-1' AND version=1;

DO $$
DECLARE v bigint;
BEGIN
  SELECT version INTO v FROM aegis_record
  WHERE tenant_id='legacy' AND dataset_id='runtime.execution' AND record_id='exec-1';
  IF v <> 2 THEN RAISE EXCEPTION 'optimistic version update failed'; END IF;
END $$;

UPDATE aegis_record
SET version=3, payload='{"state":"STALE_WRITE"}'
WHERE tenant_id='legacy' AND dataset_id='runtime.execution' AND record_id='exec-1' AND version=1;

DO $$
DECLARE v bigint;
BEGIN
  SELECT version INTO v FROM aegis_record
  WHERE tenant_id='legacy' AND dataset_id='runtime.execution' AND record_id='exec-1';
  IF v <> 2 THEN RAISE EXCEPTION 'stale optimistic write was accepted'; END IF;
END $$;

-- Projection is derived and monotonic by source_version.
INSERT INTO aegis_projection(tenant_id,projection_id,entity_id,source_version,payload)
VALUES ('legacy','execution_status','exec-1',2,'{"state":"COMPLETED"}')
ON CONFLICT (tenant_id,projection_id,entity_id) DO UPDATE
SET source_version=EXCLUDED.source_version, payload=EXCLUDED.payload
WHERE aegis_projection.source_version < EXCLUDED.source_version;

-- Older replay must not move projection backward.
INSERT INTO aegis_projection(tenant_id,projection_id,entity_id,source_version,payload)
VALUES ('legacy','execution_status','exec-1',1,'{"state":"RUNNING"}')
ON CONFLICT (tenant_id,projection_id,entity_id) DO UPDATE
SET source_version=EXCLUDED.source_version, payload=EXCLUDED.payload
WHERE aegis_projection.source_version < EXCLUDED.source_version;

DO $$
DECLARE v bigint;
BEGIN
  SELECT source_version INTO v FROM aegis_projection
  WHERE tenant_id='legacy' AND projection_id='execution_status' AND entity_id='exec-1';
  IF v <> 2 THEN RAISE EXCEPTION 'projection monotonicity failed'; END IF;
END $$;
SQL

echo "POSTGRES_LIVE_TEST=PASS"

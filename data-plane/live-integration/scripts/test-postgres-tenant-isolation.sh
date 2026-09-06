#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_PG_URL:=postgresql://aegis:${AEGIS_POSTGRES_PASSWORD}@127.0.0.1:54329/aegis}"

psql "$AEGIS_PG_URL" -v ON_ERROR_STOP=1 <<'SQL'
DROP ROLE IF EXISTS aegis_tenant_runtime;
CREATE ROLE aegis_tenant_runtime NOLOGIN;
GRANT SELECT, INSERT, UPDATE, DELETE ON aegis_record, aegis_outbox, aegis_projection TO aegis_tenant_runtime;

TRUNCATE aegis_outbox, aegis_projection, aegis_record;

SET ROLE aegis_tenant_runtime;
SET aegis.tenant_id = 'tenant-a';

INSERT INTO aegis_record(tenant_id,dataset_id,record_id,version,payload)
VALUES ('tenant-a','runtime.execution','shared-id',1,'{"owner":"tenant-a"}');
INSERT INTO aegis_outbox(event_id,tenant_id,dataset_id,record_id,record_version,event_type,payload)
VALUES ('tenant-a-event','tenant-a','runtime.execution','shared-id',1,'EXECUTION_UPDATED','{"owner":"tenant-a"}');
INSERT INTO aegis_projection(tenant_id,projection_id,entity_id,source_version,payload)
VALUES ('tenant-a','execution_status','shared-id',1,'{"owner":"tenant-a"}');

SET aegis.tenant_id = 'tenant-b';

INSERT INTO aegis_record(tenant_id,dataset_id,record_id,version,payload)
VALUES ('tenant-b','runtime.execution','shared-id',1,'{"owner":"tenant-b"}');
INSERT INTO aegis_outbox(event_id,tenant_id,dataset_id,record_id,record_version,event_type,payload)
VALUES ('tenant-b-event','tenant-b','runtime.execution','shared-id',1,'EXECUTION_UPDATED','{"owner":"tenant-b"}');
INSERT INTO aegis_projection(tenant_id,projection_id,entity_id,source_version,payload)
VALUES ('tenant-b','execution_status','shared-id',1,'{"owner":"tenant-b"}');

DO $$
DECLARE rc int; oc int; pc int; owner text;
BEGIN
  SELECT count(*), max(payload->>'owner') INTO rc, owner FROM aegis_record WHERE record_id='shared-id';
  SELECT count(*) INTO oc FROM aegis_outbox;
  SELECT count(*) INTO pc FROM aegis_projection WHERE entity_id='shared-id';
  IF rc <> 1 OR owner <> 'tenant-b' OR oc <> 1 OR pc <> 1 THEN
    RAISE EXCEPTION 'tenant-b visibility isolation failed: records %, owner %, outbox %, projection %', rc, owner, oc, pc;
  END IF;
END $$;

DO $$
BEGIN
  BEGIN
    INSERT INTO aegis_record(tenant_id,dataset_id,record_id,version,payload)
    VALUES ('tenant-a','runtime.execution','cross-write',1,'{}');
    RAISE EXCEPTION 'cross-tenant write unexpectedly succeeded';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END $$;

RESET aegis.tenant_id;

DO $$
DECLARE rc int; oc int; pc int;
BEGIN
  SELECT count(*) INTO rc FROM aegis_record;
  SELECT count(*) INTO oc FROM aegis_outbox;
  SELECT count(*) INTO pc FROM aegis_projection;
  IF rc <> 0 OR oc <> 0 OR pc <> 0 THEN
    RAISE EXCEPTION 'missing tenant context did not fail closed: records %, outbox %, projection %', rc, oc, pc;
  END IF;
END $$;

RESET ROLE;
DROP ROLE aegis_tenant_runtime;
SQL

echo "POSTGRES_TENANT_ISOLATION=PASS"

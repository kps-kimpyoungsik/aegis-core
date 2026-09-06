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

SET aegis.tenant_id = 'tenant-b';
INSERT INTO aegis_record(tenant_id,dataset_id,record_id,version,payload)
VALUES ('tenant-b','runtime.execution','shared-id',1,'{"owner":"tenant-b"}');

DO $$
DECLARE rc int; owner text;
BEGIN
  SELECT count(*), max(payload->>'owner') INTO rc, owner
  FROM aegis_record
  WHERE record_id='shared-id';
  IF rc <> 1 OR owner <> 'tenant-b' THEN
    RAISE EXCEPTION 'tenant visibility isolation failed: count %, owner %', rc, owner;
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
DECLARE rc int;
BEGIN
  SELECT count(*) INTO rc FROM aegis_record;
  IF rc <> 0 THEN
    RAISE EXCEPTION 'missing tenant context did not fail closed: count %', rc;
  END IF;
END $$;

RESET ROLE;
REVOKE ALL PRIVILEGES ON aegis_record, aegis_outbox, aegis_projection FROM aegis_tenant_runtime;
DROP ROLE aegis_tenant_runtime;
SQL

printf 'POSTGRES_TENANT_ISOLATION=PASS\n'

BEGIN;

ALTER TABLE aegis_record ADD COLUMN IF NOT EXISTS tenant_id TEXT;
ALTER TABLE aegis_outbox ADD COLUMN IF NOT EXISTS tenant_id TEXT;
ALTER TABLE aegis_projection ADD COLUMN IF NOT EXISTS tenant_id TEXT;

UPDATE aegis_record SET tenant_id = 'legacy' WHERE tenant_id IS NULL;
UPDATE aegis_outbox SET tenant_id = 'legacy' WHERE tenant_id IS NULL;
UPDATE aegis_projection SET tenant_id = 'legacy' WHERE tenant_id IS NULL;

ALTER TABLE aegis_record ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE aegis_outbox ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE aegis_projection ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE aegis_record DROP CONSTRAINT IF EXISTS aegis_record_pkey;
ALTER TABLE aegis_record ADD PRIMARY KEY (tenant_id, dataset_id, record_id);

ALTER TABLE aegis_outbox DROP CONSTRAINT IF EXISTS aegis_outbox_dataset_id_record_id_record_version_event_type_key;
ALTER TABLE aegis_outbox ADD CONSTRAINT aegis_outbox_tenant_record_version_event_unique UNIQUE (tenant_id, dataset_id, record_id, record_version, event_type);

ALTER TABLE aegis_projection DROP CONSTRAINT IF EXISTS aegis_projection_pkey;
ALTER TABLE aegis_projection ADD PRIMARY KEY (tenant_id, projection_id, entity_id);

ALTER TABLE aegis_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_record FORCE ROW LEVEL SECURITY;
ALTER TABLE aegis_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_outbox FORCE ROW LEVEL SECURITY;
ALTER TABLE aegis_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE aegis_projection FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS aegis_record_tenant_isolation ON aegis_record;
CREATE POLICY aegis_record_tenant_isolation ON aegis_record
USING (tenant_id = current_setting('aegis.tenant_id', true))
WITH CHECK (tenant_id = current_setting('aegis.tenant_id', true));

DROP POLICY IF EXISTS aegis_outbox_tenant_isolation ON aegis_outbox;
CREATE POLICY aegis_outbox_tenant_isolation ON aegis_outbox
USING (tenant_id = current_setting('aegis.tenant_id', true))
WITH CHECK (tenant_id = current_setting('aegis.tenant_id', true));

DROP POLICY IF EXISTS aegis_projection_tenant_isolation ON aegis_projection;
CREATE POLICY aegis_projection_tenant_isolation ON aegis_projection
USING (tenant_id = current_setting('aegis.tenant_id', true))
WITH CHECK (tenant_id = current_setting('aegis.tenant_id', true));

COMMIT;

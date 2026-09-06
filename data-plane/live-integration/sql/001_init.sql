BEGIN;

CREATE TABLE IF NOT EXISTS aegis_record (
    tenant_id TEXT NOT NULL,
    dataset_id TEXT NOT NULL,
    record_id TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    payload JSONB NOT NULL,
    PRIMARY KEY (tenant_id, dataset_id, record_id)
);

CREATE TABLE IF NOT EXISTS aegis_outbox (
    event_id TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    dataset_id TEXT NOT NULL,
    record_id TEXT NOT NULL,
    record_version BIGINT NOT NULL CHECK (record_version > 0),
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id),
    UNIQUE (tenant_id, dataset_id, record_id, record_version, event_type)
);

CREATE TABLE IF NOT EXISTS aegis_projection (
    tenant_id TEXT NOT NULL,
    projection_id TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version > 0),
    payload JSONB NOT NULL,
    PRIMARY KEY (tenant_id, projection_id, entity_id)
);

COMMIT;

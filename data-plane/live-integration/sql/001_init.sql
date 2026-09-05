BEGIN;

CREATE TABLE IF NOT EXISTS aegis_record (
    dataset_id TEXT NOT NULL,
    record_id TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    payload JSONB NOT NULL,
    PRIMARY KEY (dataset_id, record_id)
);

CREATE TABLE IF NOT EXISTS aegis_outbox (
    event_id TEXT PRIMARY KEY,
    dataset_id TEXT NOT NULL,
    record_id TEXT NOT NULL,
    record_version BIGINT NOT NULL CHECK (record_version > 0),
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dataset_id, record_id, record_version, event_type)
);

CREATE TABLE IF NOT EXISTS aegis_projection (
    projection_id TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version > 0),
    payload JSONB NOT NULL,
    PRIMARY KEY (projection_id, entity_id)
);

COMMIT;

BEGIN;
CREATE TABLE IF NOT EXISTS aegis_work (
  tenant_id TEXT NOT NULL, work_id TEXT NOT NULL, objective TEXT NOT NULL, aggregate_version BIGINT NOT NULL CHECK (aggregate_version >= 1),
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (tenant_id, work_id)
);
CREATE TABLE IF NOT EXISTS aegis_task (
  tenant_id TEXT NOT NULL, task_id TEXT NOT NULL, work_id TEXT NOT NULL, objective TEXT NOT NULL, lifecycle_state TEXT NOT NULL, aggregate_version BIGINT NOT NULL CHECK (aggregate_version >= 1),
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (tenant_id, task_id),
  FOREIGN KEY (tenant_id, work_id) REFERENCES aegis_work(tenant_id, work_id)
);
CREATE TABLE IF NOT EXISTS aegis_execution (
  tenant_id TEXT NOT NULL, execution_id TEXT NOT NULL, task_id TEXT NOT NULL, attempt BIGINT NOT NULL CHECK (attempt >= 1), fence_token BIGINT NOT NULL CHECK (fence_token >= 1), execution_state TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NULL, PRIMARY KEY (tenant_id, execution_id),
  FOREIGN KEY (tenant_id, task_id) REFERENCES aegis_task(tenant_id, task_id)
);
CREATE TABLE IF NOT EXISTS aegis_command_idempotency (
  tenant_id TEXT NOT NULL, idempotency_key TEXT NOT NULL, request_hash TEXT NOT NULL, status TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, idempotency_key)
);
CREATE TABLE IF NOT EXISTS aegis_outbox_event (
  tenant_id TEXT NOT NULL, event_id TEXT NOT NULL, aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL, event_type TEXT NOT NULL, payload TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ NULL,
  PRIMARY KEY (tenant_id, event_id)
);
CREATE TABLE IF NOT EXISTS aegis_event_inbox (
  consumer_id TEXT NOT NULL, event_id TEXT NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (consumer_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_aegis_outbox_unpublished ON aegis_outbox_event(created_at) WHERE published_at IS NULL;
COMMIT;

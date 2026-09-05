-- Production target projection; P4 Data Plane owns persistence mechanism.
CREATE TABLE IF NOT EXISTS aegis_task(task_id uuid PRIMARY KEY,intent text NOT NULL,state text NOT NULL,plan_json jsonb NOT NULL DEFAULT '[]'::jsonb,output_json jsonb,error text,rollback_ref text,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL);
CREATE TABLE IF NOT EXISTS aegis_episode(episode_id bigserial PRIMARY KEY,task_id uuid NOT NULL,intent text NOT NULL,state text NOT NULL,output_json jsonb,error text,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS aegis_idempotency(idempotency_key text PRIMARY KEY,payload_hash char(64) NOT NULL,task_id uuid NOT NULL,status text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());

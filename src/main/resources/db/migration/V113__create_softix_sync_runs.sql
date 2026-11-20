CREATE TABLE softix_sync_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    hidden_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500)
);

CREATE INDEX idx_softix_sync_runs_started ON softix_sync_runs(started_at DESC);

CREATE TABLE softix_sync_run_logs (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES softix_sync_runs(id) ON DELETE CASCADE,
    level VARCHAR(10) NOT NULL,
    sku VARCHAR(50),
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_softix_sync_run_logs_run ON softix_sync_run_logs(run_id, id);

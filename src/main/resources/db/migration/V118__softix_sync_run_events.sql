-- Registro por eventos de las ejecuciones Softix. No hay datos que conservar:
-- las ejecuciones anteriores se borran junto con su detalle.

DELETE FROM softix_sync_runs;

DROP TABLE IF EXISTS softix_sync_run_logs;

ALTER TABLE softix_sync_runs
    ADD COLUMN created_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN restored_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN new_brand_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN new_category_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE softix_sync_run_events (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES softix_sync_runs(id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,
    entity_key VARCHAR(255),
    action VARCHAR(20) NOT NULL,
    level VARCHAR(10) NOT NULL,
    field_name VARCHAR(30),
    old_value TEXT,
    new_value TEXT,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_softix_sync_run_events_run ON softix_sync_run_events(run_id, id);
CREATE INDEX idx_softix_sync_run_events_created ON softix_sync_run_events(created_at);

ALTER TABLE softix_sync_runs DROP COLUMN created_count;
ALTER TABLE softix_sync_runs RENAME COLUMN skipped_count TO error_count;
ALTER TABLE softix_sync_runs ADD COLUMN pending_new_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE softix_sync_run_logs ADD COLUMN field_name VARCHAR(30);
ALTER TABLE softix_sync_run_logs ADD COLUMN old_value TEXT;
ALTER TABLE softix_sync_run_logs ADD COLUMN new_value TEXT;

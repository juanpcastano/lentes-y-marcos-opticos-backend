ALTER TABLE users
    ADD COLUMN softix_client_id VARCHAR(100),
    ADD COLUMN softix_document_type VARCHAR(20),
    ADD COLUMN softix_document_id VARCHAR(100);

CREATE UNIQUE INDEX uq_users_softix_client_id
    ON users (softix_client_id)
    WHERE softix_client_id IS NOT NULL;

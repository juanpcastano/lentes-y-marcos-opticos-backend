-- Identidad por teléfono verificado con OTP por WhatsApp.
-- No hay usuarios reales todavía (pre-1.0): la cuenta local se reduce al
-- teléfono, el vínculo con Softix y la evidencia de consentimiento.

DELETE FROM auth_codes;
DELETE FROM users;

ALTER TABLE users DROP COLUMN IF EXISTS name;
ALTER TABLE users DROP COLUMN IF EXISTS email;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE users DROP COLUMN IF EXISTS google_sub;
ALTER TABLE users DROP COLUMN IF EXISTS phone;
ALTER TABLE users DROP COLUMN IF EXISTS softix_document_type;
ALTER TABLE users DROP COLUMN IF EXISTS softix_document_id;

ALTER TABLE users
    ADD COLUMN phone_e164 VARCHAR(20) NOT NULL,
    ADD COLUMN phone_verified_at TIMESTAMP,
    ADD COLUMN softix_linked_at TIMESTAMP,
    ADD COLUMN consent_version VARCHAR(20),
    ADD COLUMN consent_at TIMESTAMP,
    ADD COLUMN consent_ip VARCHAR(45),
    ADD COLUMN last_login_at TIMESTAMP;

CREATE UNIQUE INDEX uq_users_phone_e164 ON users (phone_e164);

DROP TABLE auth_codes;

CREATE TABLE auth_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    identifier VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    purpose VARCHAR(20) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    request_ip VARCHAR(45),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_codes_identifier_purpose
    ON auth_codes (identifier, purpose);
CREATE INDEX idx_auth_codes_created_at
    ON auth_codes (created_at);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);

CREATE TABLE softix_customer_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_e164 VARCHAR(20) NOT NULL,
    softix_client_id VARCHAR(100) NOT NULL,
    synced_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_softix_customer_index UNIQUE (phone_e164, softix_client_id)
);

CREATE INDEX idx_softix_customer_index_phone ON softix_customer_index (phone_e164);
CREATE INDEX idx_softix_customer_index_client ON softix_customer_index (softix_client_id);

CREATE TABLE softix_link_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    phone_e164 VARCHAR(20) NOT NULL,
    candidate_client_ids TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_softix_link_challenges_user ON softix_link_challenges (user_id);

CREATE TABLE softix_index_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    total_records INT,
    unique_clients INT,
    indexed_phones INT,
    message VARCHAR(500)
);

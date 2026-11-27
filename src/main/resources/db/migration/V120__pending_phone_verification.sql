-- El OTP ya no crea la cuenta. Deja una verificación pendiente y la fila de
-- `users` se crea únicamente al completar la vinculación con Softix.

CREATE TABLE phone_verifications (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(20) NOT NULL,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    consent_version VARCHAR(20),
    consent_at TIMESTAMP,
    consent_ip VARCHAR(45),
    expires_at TIMESTAMP NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_phone_verifications_phone ON phone_verifications (phone_e164);

DROP TABLE softix_link_challenges;

CREATE TABLE softix_link_challenges (
    id UUID PRIMARY KEY,
    phone_verification_id UUID NOT NULL REFERENCES phone_verifications (id) ON DELETE CASCADE,
    phone_e164 VARCHAR(20) NOT NULL,
    candidate_client_ids TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_softix_link_challenges_verification
    ON softix_link_challenges (phone_verification_id);
CREATE INDEX idx_softix_link_challenges_phone
    ON softix_link_challenges (phone_e164);

-- Invariante: no puede existir una cuenta sin ficha de Softix vinculada.
DELETE FROM users WHERE softix_client_id IS NULL OR softix_linked_at IS NULL;

ALTER TABLE users ALTER COLUMN phone_verified_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN softix_client_id SET NOT NULL;
ALTER TABLE users ALTER COLUMN softix_linked_at SET NOT NULL;

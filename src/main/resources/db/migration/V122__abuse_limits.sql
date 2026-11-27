-- Límites de abuso del flujo de identidad.
-- 1. Identificador de dispositivo para limitar el inicio de OTP.
-- 2. Registro de escrituras en Softix para limitar creación/edición por usuario.

ALTER TABLE auth_codes
    ADD COLUMN device_id VARCHAR(64);

CREATE INDEX idx_auth_codes_device_created
    ON auth_codes (device_id, created_at);

CREATE INDEX idx_auth_codes_channel_created
    ON auth_codes (channel, created_at);

CREATE TABLE softix_write_events (
    id UUID PRIMARY KEY,
    subject_key VARCHAR(100) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_softix_write_events_subject_created
    ON softix_write_events (subject_key, created_at);

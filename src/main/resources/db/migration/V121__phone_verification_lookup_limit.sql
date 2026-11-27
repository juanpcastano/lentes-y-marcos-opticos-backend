-- Limita la enumeración de documentos desde el formulario de registro.
-- Cada teléfono verificado puede consultar como máximo 5 documentos.
ALTER TABLE phone_verifications
    ADD COLUMN document_lookup_attempts INT NOT NULL DEFAULT 0;

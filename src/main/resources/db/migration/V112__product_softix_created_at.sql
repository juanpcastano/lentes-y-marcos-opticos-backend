-- Fecha de creacion del producto segun Softix (campo creationDate del esquema Product).
-- Se usa para el calculo de "novedad" en lugar de la fecha local de sincronizacion.
ALTER TABLE products ADD COLUMN softix_created_at TIMESTAMP NULL;
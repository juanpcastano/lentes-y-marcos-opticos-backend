-- V107: la disponibilidad vive solo en la variante (product_variants.is_active).
-- El flag a nivel producto quedó redundante y confunde (un producto "activo"
-- sin variantes activas igual no se vende), así que se elimina.
ALTER TABLE products DROP COLUMN IF EXISTS is_active;

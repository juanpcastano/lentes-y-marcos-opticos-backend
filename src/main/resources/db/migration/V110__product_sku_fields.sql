-- V110: cada SKU de Softix es un producto. Se agregan las columnas que hoy viven
-- en la variante; las variantes se eliminan en un paso posterior.
-- Los datos de Softix (SKU, marca, referencia, nombre, color, categoría, precio
-- y stock) son de solo lectura en el admin.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS sku VARCHAR(50) UNIQUE,
    ADD COLUMN IF NOT EXISTS reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS color VARCHAR(100),
    ADD COLUMN IF NOT EXISTS price NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS sale_vat BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'HIDDEN')),
    ADD COLUMN IF NOT EXISTS softix_missing BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_reference ON products(reference);

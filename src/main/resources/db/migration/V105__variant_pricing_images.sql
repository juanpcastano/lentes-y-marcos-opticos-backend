-- V105: el precio/descuento/color viven en la variante; las imágenes cuelgan
-- de la variante (nueva tabla variant_images). Backfill desde el esquema
-- anterior para no perder los datos existentes (dev ya tiene ~1.100 SKUs).
--
-- V106 (siguiente migración) impone los NOT NULL y elimina las columnas y
-- tablas viejas, una vez migrados los datos.

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS color VARCHAR(100),
    ADD COLUMN IF NOT EXISTS price INTEGER,
    ADD COLUMN IF NOT EXISTS discount_percentage INTEGER
        CHECK (discount_percentage BETWEEN 0 AND 100);

CREATE TABLE IF NOT EXISTS variant_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    sort_order INTEGER
);

-- Precio/descuento: se heredan del producto; color: UPPER(variant_name).
-- 'Color' (seed) y 'Único' (backfill V101 / import) son el comodín
-- single-color → se normalizan a 'ÚNICO'.
UPDATE product_variants v
SET color = CASE
        WHEN UPPER(TRIM(COALESCE(v.variant_name, ''))) IN ('COLOR', 'ÚNICO', 'UNICO', '') THEN 'ÚNICO'
        ELSE UPPER(TRIM(v.variant_name))
    END,
    price = p.base_price,
    discount_percentage = p.discount_percentage
FROM products p
WHERE v.product_id = p.id
  AND (v.color IS NULL OR v.price IS NULL);

-- Imágenes de producto → imágenes de su primera variante (por id).
-- Se conservan image_url, is_primary y sort_order tal cual.
INSERT INTO variant_images (id, variant_id, image_url, is_primary, sort_order)
SELECT pi.id, first_variant.id, pi.image_url, pi.is_primary, pi.sort_order
FROM product_images pi
JOIN LATERAL (
    SELECT v.id
    FROM product_variants v
    WHERE v.product_id = pi.product_id
    ORDER BY v.id
    LIMIT 1
) first_variant ON TRUE
ON CONFLICT (id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_variant_images_variant_id
    ON variant_images(variant_id);
CREATE INDEX IF NOT EXISTS idx_product_variants_product_id
    ON product_variants(product_id);

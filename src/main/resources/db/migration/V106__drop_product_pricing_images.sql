-- V106: consolida el nuevo modelo (requiere V105 aplicado con backfill).
-- Las variantes son la unidad vendible/visible: color + precio + descuento
-- propios, con sus imágenes en variant_images. El producto queda como
-- familia (sin precio ni imágenes propias).

ALTER TABLE product_variants ALTER COLUMN color SET NOT NULL;
ALTER TABLE product_variants ALTER COLUMN price SET NOT NULL;
ALTER TABLE product_variants ALTER COLUMN sku SET NOT NULL;

-- Un color por producto (case-insensitive).
CREATE UNIQUE INDEX IF NOT EXISTS uq_variants_product_color
    ON product_variants(product_id, LOWER(color));

DROP TABLE IF EXISTS product_images;

ALTER TABLE product_variants DROP COLUMN IF EXISTS image_url;
ALTER TABLE product_variants DROP COLUMN IF EXISTS variant_name;

ALTER TABLE products DROP COLUMN IF EXISTS base_price;
ALTER TABLE products DROP COLUMN IF EXISTS discount_percentage;

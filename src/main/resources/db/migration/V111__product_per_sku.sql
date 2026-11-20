-- V111: un producto por SKU. Se eliminan las variantes; las imágenes cuelgan del
-- producto. Las tablas de variantes no tienen datos que conservar.

DROP TABLE IF EXISTS variant_images;
DROP TABLE IF EXISTS product_variants;

CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    sort_order INTEGER
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);

ALTER TABLE products ALTER COLUMN sku SET NOT NULL;

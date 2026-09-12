ALTER TABLE product_variants
    DROP COLUMN IF EXISTS variant_value,
    DROP COLUMN IF EXISTS price_adjustment;

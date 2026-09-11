-- Backfill legacy products created before variants were required.
INSERT INTO product_variants (
    id,
    product_id,
    variant_name,
    variant_value,
    sku,
    price_adjustment,
    image_url,
    is_active
)
SELECT
    uuid_generate_v4(),
    p.id,
    'Único',
    NULL,
    'AUTO-' || replace(p.id::text, '-', ''),
    NULL,
    NULL,
    TRUE
FROM products p
WHERE NOT EXISTS (
    SELECT 1
    FROM product_variants v
    WHERE v.product_id = p.id
);

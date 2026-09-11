-- Taxonomy records cannot be deleted while products still reference them.
ALTER TABLE product_categories
    DROP CONSTRAINT IF EXISTS product_categories_category_id_fkey;

ALTER TABLE product_categories
    ADD CONSTRAINT product_categories_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT;

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_brand_id_fkey;

ALTER TABLE products
    ADD CONSTRAINT products_brand_id_fkey
    FOREIGN KEY (brand_id) REFERENCES brands(id) ON DELETE RESTRICT;

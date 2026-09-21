-- Seed de catálogo (solo dev). Excluir de prod via spring.flyway.locations.

-- Marcas, categorías, productos y variantes: ya no se seden. Todo entra por
-- el wizard de importación (/admin/import → POST /api/admin/inventory/confirm-plan).
SELECT 1;

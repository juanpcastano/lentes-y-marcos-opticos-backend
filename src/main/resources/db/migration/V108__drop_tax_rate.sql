-- La plataforma no registra IVA: la facturación lo maneja el cliente.
ALTER TABLE products DROP COLUMN IF EXISTS tax_rate;

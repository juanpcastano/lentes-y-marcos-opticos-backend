package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;

/**
 * Un SKU nuevo en Softix y su estado de importación:
 * VALID (se importará), BLOCKED (inválido, con motivo),
 * IGNORED_BY_BRAND o IGNORED_BY_SKU (no se importará por una lista de ignorados).
 */
public record SoftixImportNewSkuDto(
        String sku,
        String brand,
        String reference,
        String color,
        String category,
        String name,
        BigDecimal price,
        int stock,
        String state,
        String blockReason) {
}

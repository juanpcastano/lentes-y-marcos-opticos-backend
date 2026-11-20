package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;

/**
 * Un SKU nuevo en Softix que podría importarse. `blocked` indica que tiene un error bloqueante
 * y no se creará hasta corregirlo en Softix. `ignored` indica que su marca está ignorada.
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
        boolean blocked,
        String blockReason,
        boolean ignored) {
}
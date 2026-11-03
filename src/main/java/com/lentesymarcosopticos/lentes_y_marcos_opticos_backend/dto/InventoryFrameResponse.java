package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;

public record InventoryFrameResponse(
        String sku,
        String description,
        Integer quantity,
        BigDecimal price,
        BigDecimal maxDiscount) {
}

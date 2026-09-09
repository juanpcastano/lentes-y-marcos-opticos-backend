package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.UUID;

/**
 * VariantDto
 */
public record VariantDto(
		UUID id,
		String variantName,
		String variantValue,
		String sku,
		Integer priceAdjustment,
		String imageUrl) {
}

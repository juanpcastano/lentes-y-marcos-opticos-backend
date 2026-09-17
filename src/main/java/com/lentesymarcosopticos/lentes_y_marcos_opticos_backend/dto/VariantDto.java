package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.UUID;

/**
 * VariantDto
 */
public record VariantDto(
		UUID id,
		String color,
		String sku,
		Integer price,
		Integer discountPercentage,
		Integer discountedPrice,
		Boolean isActive) {
}

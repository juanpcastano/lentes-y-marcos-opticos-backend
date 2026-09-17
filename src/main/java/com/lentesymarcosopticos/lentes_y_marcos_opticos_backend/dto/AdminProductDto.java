package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AdminProductDto — producto completo para el panel de administración.
 * Las variantes incluyen sus imágenes.
 */
public record AdminProductDto(
		UUID id,
		String name,
		UUID brandId,
		String brand,
		String material,
		String shape,
		String description,
		BigDecimal taxRate,
		String productType,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		List<String> categories,
		List<AdminVariantDto> variants) {

	public record AdminVariantDto(
			UUID id,
			String color,
			String sku,
			Integer price,
			Integer discountPercentage,
			Integer discountedPrice,
			Boolean isActive,
			List<ProductImageDto> images) {
	}
}

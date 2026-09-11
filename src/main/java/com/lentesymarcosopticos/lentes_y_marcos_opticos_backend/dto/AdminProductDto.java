package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AdminProductDto — producto completo para el panel de administración
 */
public record AdminProductDto(
		UUID id,
		String name,
		UUID brandId,
		String brand,
		Integer basePrice,
		Integer discountPercentage,
		String material,
		String shape,
		String description,
		BigDecimal taxRate,
		String productType,
		Boolean isActive,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		List<String> categories,
		List<ProductImageDto> images,
		List<VariantDto> variants) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;

/**
 * AdminProductDto — un SKU para el panel de administración. Los campos de Softix
 * son de solo lectura; material, forma, descripción y estado sí se editan.
 * softixMissing = true significa que el SKU ya no aparece en Softix.
 */
public record AdminProductDto(
		UUID id,
		String sku,
		String name,
		String brand,
		String reference,
		String color,
		List<String> categories,
		BigDecimal price,
		Boolean saleVat,
		Integer stock,
		ProductStatus status,
		Boolean softixMissing,
		LocalDateTime syncedAt,
		String material,
		String shape,
		String description,
		String imageUrl,
		List<ProductImageDto> images) {
}

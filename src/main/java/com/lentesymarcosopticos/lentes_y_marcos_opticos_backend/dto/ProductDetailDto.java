package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;

/**
 * ProductDetailDto — ficha de un SKU. available = activo con stock.
 * otherColors: mismo marca y referencia, otro SKU ("Otros colores").
 */
public record ProductDetailDto(
		UUID id,
		String sku,
		String name,
		String brand,
		String reference,
		String color,
		BigDecimal price,
		Integer stock,
		boolean available,
		ProductStatus status,
		String imageUrl,
		List<String> additionalImages,
		String material,
		String shape,
		List<String> categories,
		String badge,
		String description,
		List<OtherColorDto> otherColors) {

	public record OtherColorDto(UUID id, String color, String imageUrl, boolean available) {
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * ProductDetailDto — detalle con imágenes, variantes y precios calculados
 */
public record ProductDetailDto(
		UUID id,
		String imageUrl,
		String name,
		String brand,
		Integer price,
		String material,
		String shape,
		List<String> categories,
		String badge,
		List<String> additionalImages,
		Integer originalPrice,
		Integer discountedPrice,
		Integer discountPercentage,
		String description,
		List<VariantDto> variants,
		Boolean isActive) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * ProductDetailDto — detalle de producto con variante seleccionada.
 * price = precio final de la seleccionada (compat).
 * id y productId son el id del producto (productId lo usa el carrito).
 */
public record ProductDetailDto(
		UUID id,
		UUID productId,
		UUID variantId,
		String imageUrl,
		String name,
		String color,
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
		VariantDto selectedVariant) {
}

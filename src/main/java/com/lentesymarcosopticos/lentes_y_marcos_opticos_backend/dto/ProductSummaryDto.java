package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * ProductSummaryDto — una fila por variante activa: brand como string,
 * categories como string[], imageUrl = imagen primaria de la variante.
 * Badge: OFERTA si la variante tiene descuento; NUEVO si producto &lt; 30 días.
 */
public record ProductSummaryDto(
		UUID variantId,
		UUID productId,
		String imageUrl,
		String name,
		String color,
		String brand,
		Integer price,
		Integer originalPrice,
		Integer discountPercentage,
		String material,
		String shape,
		List<String> categories,
		String badge) {
}

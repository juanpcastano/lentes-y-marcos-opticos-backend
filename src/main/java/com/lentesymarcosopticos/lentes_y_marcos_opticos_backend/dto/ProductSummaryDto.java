package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ProductSummaryDto — una fila por SKU disponible. El título en el catálogo es
 * marca + name + referencia + color.
 * Badge: NUEVO si el producto tiene menos de 30 días; si no, null.
 */
public record ProductSummaryDto(
		UUID id,
		String imageUrl,
		String name,
		String color,
		String brand,
		String reference,
		BigDecimal price,
		Integer stock,
		String material,
		String shape,
		List<String> categories,
		String badge) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * ProductSummaryDto — DTO "achatado" para el catálogo: brand como string,
 * categories como string[], imageUrl = imagen primaria. El badge es derivado:
 * OFERTA si hay descuento; NUEVO si tiene &lt; 30 días.
 */
public record ProductSummaryDto(
		UUID id,
		String imageUrl,
		String name,
		String brand,
		Integer price,
		String material,
		String shape,
		List<String> categories,
		String badge) {
}

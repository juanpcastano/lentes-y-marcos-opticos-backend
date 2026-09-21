package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * InventoryPreviewRowDto — una fila del Excel ya normalizada y clasificada
 * contra la DB. Status: NUEVO | ACTUALIZAR | SIN_CAMBIOS | CONFLICTO | ERROR.
 * suggestedAction: CREATE | UPDATE | NONE | DECIDE | SKIP.
 *
 * productName/color: la Descripción se parte en nombre base + color sugerido
 * (última palabra, o dos si es compuesta como "AZUL OSCURO"). Si no se
 * reconoce color, color = "ÚNICO". needsColorReview = true cuando el color
 * se infirió de un marcador ambiguo (VARIOS, "NEGRA/GRIS", ...) y el admin
 * debería revisarlo en el paso de productos. groupKey agrupa filas que
 * parecen el mismo producto con distintas variantes (marca + nombre base
 * normalizados).
 */
public record InventoryPreviewRowDto(
		String rowKey,
		String sheet,
		int rowNumber,
		String sku,
		String name,
		String brand,
		List<String> categories,
		String productType,
		Integer basePrice,
		String status,
		String detail,
		ExistingProductSnapshot existing,
		String suggestedAction,
		String productName,
		String color,
		boolean needsColorReview,
		String groupKey) {

	public record ExistingProductSnapshot(
			String name,
			Integer basePrice,
			String brand,
			List<String> categories,
			String productType,
			String productId) {
	}
}

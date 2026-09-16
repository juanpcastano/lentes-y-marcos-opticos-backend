package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * InventoryPreviewRowDto — una fila del Excel ya normalizada y clasificada
 * contra la DB. Status: NUEVO | ACTUALIZAR | SIN_CAMBIOS | CONFLICTO | ERROR.
 * suggestedAction: CREATE | UPDATE | NONE | DECIDE | SKIP.
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
		String suggestedAction) {

	public record ExistingProductSnapshot(
			String name,
			Integer basePrice,
			String brand,
			List<String> categories,
			String productType) {
	}
}

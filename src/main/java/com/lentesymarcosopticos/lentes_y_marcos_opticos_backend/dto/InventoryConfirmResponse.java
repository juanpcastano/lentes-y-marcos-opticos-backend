package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * InventoryConfirmResponse — resultado de aplicar la importación
 */
public record InventoryConfirmResponse(
		int created,
		int updated,
		int discarded,
		int unchanged,
		List<String> skipped) {
}

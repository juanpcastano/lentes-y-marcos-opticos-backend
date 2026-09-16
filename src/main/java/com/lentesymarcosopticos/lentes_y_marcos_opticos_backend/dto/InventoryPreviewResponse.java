package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * InventoryPreviewResponse — resultado del preview (sin persistir nada)
 */
public record InventoryPreviewResponse(
		List<InventoryPreviewRowDto> rows,
		Summary summary) {

	public record Summary(
			int total,
			int nuevos,
			int actualizar,
			int sinCambios,
			int conflictos,
			int errores) {
	}
}

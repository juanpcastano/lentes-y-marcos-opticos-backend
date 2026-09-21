package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * InventoryPreviewResponse — resultado del preview (sin persistir nada).
 * Además de las filas, devuelve las marcas y categorías detectadas en el
 * Excel (con conteo y sugerencia de match contra la tienda) y el catálogo
 * existente de marcas/categorías para poblar los selects del wizard.
 */
public record InventoryPreviewResponse(
		List<InventoryPreviewRowDto> rows,
		Summary summary,
		List<BrandCandidate> brands,
		List<CategoryCandidate> categories,
		List<ExistingBrand> existingBrands,
		List<ExistingCategory> existingCategories) {

	public record Summary(
			int total,
			int nuevos,
			int actualizar,
			int sinCambios,
			int conflictos,
			int errores) {
	}

	public record BrandCandidate(
			String rawName,
			int count,
			UUID suggestedBrandId,
			String suggestedBrandName) {
	}

	public record CategoryCandidate(
			String rawName,
			int count,
			UUID suggestedCategoryId,
			String suggestedCategoryName) {
	}

	public record ExistingBrand(
			UUID id,
			String name) {
	}

	public record ExistingCategory(
			UUID id,
			String name) {
	}
}

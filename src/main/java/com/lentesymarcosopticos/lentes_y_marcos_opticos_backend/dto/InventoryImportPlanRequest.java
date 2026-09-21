package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * InventoryImportPlanRequest — plan curado por el admin en el wizard de
 * importación (pasos de marcas, categorías y productos). El backend lo valida
 * completo antes de aplicar nada: cualquier violación responde 422 sin
 * persistir.
 */
public record InventoryImportPlanRequest(
		List<BrandMapping> brands,
		List<CategoryMapping> categories,
		List<PlannedProduct> products) {

	/**
	 * Mapeo de una marca detectada en el Excel. action: MAP (usa brandId
	 * existente) | CREATE (crea con finalName) | OMIT (excluye la marca y
	 * todos sus productos).
	 */
	public record BrandMapping(
			String rawName,
			String action,
			UUID brandId,
			String finalName) {
	}

	/**
	 * Mapeo de una categoría detectada en el Excel. action: MAP | CREATE |
	 * OMIT (la categoría se quita de los productos; si un producto queda sin
	 * categorías, se importa sin ellas).
	 */
	public record CategoryMapping(
			String rawName,
			String action,
			UUID categoryId,
			String finalName) {
	}

	/**
	 * Producto final a crear o actualizar. existingProductId enlaza con un
	 * producto de la tienda (merge manual); null crea uno nuevo. brandRaw y
	 * categoryRaws referencian los rawName del Excel resueltos con los mapeos.
	 */
	public record PlannedProduct(
			UUID existingProductId,
			String name,
			String brandRaw,
			List<String> categoryRaws,
			String productType,
			List<PlannedVariant> variants) {
	}

	/**
	 * Variante final. mode: CREATE (SKU nuevo) | UPDATE (solo precio, color y
	 * nombre del producto) | REPLACE (todo, como UPDATE más marca/categorías/
	 * tipo) | DISCARD (no se importa esta fila).
	 */
	public record PlannedVariant(
			String sku,
			String color,
			Integer price,
			String mode) {
	}
}

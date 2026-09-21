package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * ProductUpsertRequest — creación/edición de producto desde admin.
 * En PUT, los campos null/no presentes dejan el valor actual;
 * categories/variants null = no tocar, lista vacía = vaciar.
 * Las imágenes de variantes ya guardadas se gestionan por endpoints
 * dedicados de variante; `images` solo aplica a variantes nuevas
 * (id null) y referencia objetos ya subidos a `variants/` (staging sin
 * referencias: al guardar se crean las filas VariantImage).
 */
public record ProductUpsertRequest(
		@NotBlank String name,
		UUID brandId,
		String material,
		String shape,
		String description,
		@NotBlank String productType,
		List<String> categories,
		List<VariantRequest> variants) {

	public record VariantRequest(
			UUID id,
			String color,
			String sku,
			@Positive Integer price,
			@Min(0) @Max(100) Integer discountPercentage,
			Boolean isActive,
			List<VariantImageRequest> images) {
	}

	public record VariantImageRequest(
			String imageUrl,
			Boolean primary) {
	}
}

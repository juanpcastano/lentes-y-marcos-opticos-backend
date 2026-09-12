package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * ProductUpsertRequest — creación/edición de producto desde admin.
 * En PUT, los campos null/no presentes dejan el valor actual;
 * categories/variants null = no tocar, lista vacía = vaciar.
 */
public record ProductUpsertRequest(
		@NotBlank String name,
		UUID brandId,
		@NotNull @Positive Integer basePrice,
		@Min(0) @Max(100) Integer discountPercentage,
		String material,
		String shape,
		String description,
		BigDecimal taxRate,
		@NotBlank String productType,
		Boolean isActive,
		List<String> categories,
		List<VariantRequest> variants) {

		public record VariantRequest(
				UUID id,
				String variantName,
				String sku,
				String imageUrl,
				Boolean isActive) {
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * BrandRequest — creación/edición de marca desde admin
 */
public record BrandRequest(
		@NotBlank String name,
		String tagline,
		String imageUrl,
		Boolean isFeatured) {
}

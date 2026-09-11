package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * CategoryRequest — creación/edición de categoría desde admin
 */
public record CategoryRequest(
		@NotBlank String name,
		String description,
		String imageUrl,
		Boolean isFeatured) {
}

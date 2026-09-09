package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.UUID;

/**
 * CategoryDto
 */
public record CategoryDto(
		UUID id,
		String name,
		String description,
		String imageUrl,
		Boolean isFeatured) {
}

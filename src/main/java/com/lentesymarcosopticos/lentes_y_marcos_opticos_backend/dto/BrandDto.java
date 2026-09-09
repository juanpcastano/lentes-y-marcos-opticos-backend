package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.UUID;

/**
 * BrandDto
 */
public record BrandDto(
		UUID id,
		String name,
		String tagline,
		String imageUrl,
		Boolean isFeatured) {
}

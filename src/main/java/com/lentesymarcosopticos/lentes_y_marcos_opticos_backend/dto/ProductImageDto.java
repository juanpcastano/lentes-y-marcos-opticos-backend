package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.UUID;

/**
 * ProductImageDto
 */
public record ProductImageDto(
		UUID id,
		String imageUrl,
		Boolean isPrimary,
		Integer sortOrder) {
}

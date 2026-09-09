package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * PageResponse — wrapper propio de paginación (shape estable: content, page,
 * size, totalElements, totalPages)
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}

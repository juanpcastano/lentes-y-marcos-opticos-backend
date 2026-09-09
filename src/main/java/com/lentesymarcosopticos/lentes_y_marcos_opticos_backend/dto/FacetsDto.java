package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * FacetsDto — valores disponibles para los filtros del catálogo
 */
public record FacetsDto(
		List<String> materials,
		List<String> shapes,
		Integer minPrice,
		Integer maxPrice) {
}

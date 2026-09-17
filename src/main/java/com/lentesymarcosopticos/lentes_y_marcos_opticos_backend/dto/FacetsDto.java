package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * FacetsDto — valores disponibles para los filtros del catálogo.
 * colors = colores de variantes visibles (una fila por color en catálogo).
 */
public record FacetsDto(
		List<String> materials,
		List<String> shapes,
		List<String> colors,
		Integer minPrice,
		Integer maxPrice) {
}

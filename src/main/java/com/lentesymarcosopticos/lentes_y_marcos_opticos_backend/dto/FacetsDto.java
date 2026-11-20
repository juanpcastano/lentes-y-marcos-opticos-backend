package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * FacetsDto — valores disponibles para los filtros del catálogo. Solo cuentan
 * productos visibles (activos con stock).
 */
public record FacetsDto(
		List<String> brands,
		List<String> categories,
		List<String> materials,
		List<String> shapes,
		List<String> colors,
		BigDecimal minPrice,
		BigDecimal maxPrice) {
}

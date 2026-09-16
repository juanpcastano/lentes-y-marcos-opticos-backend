package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * HeroSlideDto — forma pública y admin del slide del hero.
 * Las acciones se derivan de (ctaLabel, ctaTo) y (cta2Label, cta2To).
 */
public record HeroSlideDto(
		UUID id,
		String title,
		String description,
		String imageUrl,
		List<HeroSlideActionDto> actions,
		Integer sortOrder,
		Boolean isActive) {

	public record HeroSlideActionDto(String label, String to) {
	}
}

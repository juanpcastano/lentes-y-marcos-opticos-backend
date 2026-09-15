package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

public record MediaAssetDto(
		String key,
		String imageUrl,
		String folder,
		List<MediaReferenceDto> references) {

	public boolean used() {
		return !references.isEmpty();
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

/**
 * RefreshResponse
 */
public record RefreshResponse(
		String accessToken,
		UserResponse user) {
}

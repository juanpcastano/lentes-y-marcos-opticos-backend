package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

/**
 * AuthResponse
 */
public record AuthResponse(
		String accessToken,
		UserResponse user) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

public record AuthResult(
		String accessToken,
		String refreshToken,
		UserResponse user) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

public record CodeStatusResponse(
		boolean active,
		long cooldownSeconds) {
}

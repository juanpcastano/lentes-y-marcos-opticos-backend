package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest
 */
public record LoginRequest(
		@NotBlank String email,
		@NotBlank String password) {
}

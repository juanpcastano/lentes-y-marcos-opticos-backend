package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneStartRequest(
		@NotBlank(message = "Ingresa tu número de celular") String phone,
		String captchaToken) {
}

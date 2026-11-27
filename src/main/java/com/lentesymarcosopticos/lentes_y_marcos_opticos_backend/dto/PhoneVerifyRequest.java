package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerifyRequest(
		@NotBlank(message = "Ingresa tu número de celular") String phone,
		@NotBlank(message = "Ingresa el código")
		@Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos") String code,
		Boolean consentAccepted) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneChallengeConfirmRequest(
		@NotBlank String challengeId,
		@NotBlank
		@Pattern(regexp = "\\d{1,10}", message = "Ingresa el número de documento")
		String documentId) {
}

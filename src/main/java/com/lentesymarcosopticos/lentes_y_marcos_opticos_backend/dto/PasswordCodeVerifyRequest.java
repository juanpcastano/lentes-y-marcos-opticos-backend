package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordCodeVerifyRequest(
		@NotBlank @Size(min = 6, max = 6) String code) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginCodeRequest(
		@NotBlank @Email String email) {
}

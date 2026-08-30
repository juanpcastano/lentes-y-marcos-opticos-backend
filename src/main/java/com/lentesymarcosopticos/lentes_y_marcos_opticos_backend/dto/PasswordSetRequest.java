package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordSetRequest(
		@NotBlank @Size(min = 6, max = 6) String code,
		@NotBlank @ValidPassword String newPassword) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * SignupRequest
 */
public record SignupRequest(
		@NotBlank(message = "El nombre es obligatorio") String name,
		@NotBlank(message = "El email es obligatorio")
		@Email(message = "Ingresa un email válido") String email,
		@NotBlank(message = "La contraseña es obligatoria") @ValidPassword String password,
		@NotBlank(message = "El teléfono es obligatorio") String phone) {
}

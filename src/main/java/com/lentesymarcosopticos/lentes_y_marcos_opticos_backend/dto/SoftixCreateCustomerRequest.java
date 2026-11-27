package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos mínimos para crear la ficha cuando el teléfono verificado no tiene
 * ninguna. Ciudad y localidad se fijan en el backend (Cali: 76/001). Código
 * postal, estado civil y ocupación no se preguntan.
 */
public record SoftixCreateCustomerRequest(
		@NotBlank @Size(max = 2) String documentType,
		@NotBlank @Pattern(regexp = "\\d{1,10}", message = "Ingresa el número de documento") String documentId,
		@NotBlank String firstName,
		String middleName,
		@NotBlank String lastName,
		String secondLastName,
		@Email(message = "Ingresa un email válido") String email,
		@NotNull(message = "Ingresa tu fecha de nacimiento")
		@PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
		LocalDate birthDate,
		@NotBlank String gender) {
}

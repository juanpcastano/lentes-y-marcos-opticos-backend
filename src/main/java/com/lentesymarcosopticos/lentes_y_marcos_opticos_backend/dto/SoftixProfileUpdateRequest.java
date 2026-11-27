package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;

/**
 * Lista blanca mínima de edición. Documento, teléfono, ciudad, localidad,
 * código postal, estado civil y ocupación no se editan en autoservicio.
 */
public record SoftixProfileUpdateRequest(
		String firstName,
		String middleName,
		String lastName,
		String secondLastName,
		@Email(message = "Ingresa un email válido") String email,
		@PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
		LocalDate birthDate,
		String gender) {
}

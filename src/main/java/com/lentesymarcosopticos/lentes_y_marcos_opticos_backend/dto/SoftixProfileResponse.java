package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDate;

/** Datos personales de la ficha que el usuario puede ver y editar. */
public record SoftixProfileResponse(
		String documentType,
		String maskedDocumentId,
		String firstName,
		String middleName,
		String lastName,
		String secondLastName,
		String email,
		LocalDate birthDate,
		String gender,
		String city,
		String locality,
		String postalCode,
		String maritalStatus,
		String occupation) {
}

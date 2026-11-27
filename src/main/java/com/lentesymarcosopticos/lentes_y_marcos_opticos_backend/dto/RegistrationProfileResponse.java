package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDate;

/**
 * Datos de la ficha que el usuario revisa y edita antes de crear su cuenta.
 * `LINK` significa que ya existe una ficha en Softix y se vinculará al
 * confirmar; `CREATE` significa que no existe y se creará al enviar el
 * formulario.
 */
public record RegistrationProfileResponse(
		Status status,
		String documentType,
		String documentId,
		String firstName,
		String middleName,
		String lastName,
		String secondLastName,
		String email,
		LocalDate birthDate,
		String gender) {

	public enum Status {
		LINK,
		CREATE
	}

	public static RegistrationProfileResponse create(String documentType, String documentId) {
		return new RegistrationProfileResponse(Status.CREATE, documentType, documentId,
				null, null, null, null, null, null, null);
	}

	public static RegistrationProfileResponse link(String documentType, String documentId,
			String firstName, String middleName, String lastName, String secondLastName,
			String email, LocalDate birthDate, String gender) {
		return new RegistrationProfileResponse(Status.LINK, documentType, documentId,
				firstName, middleName, lastName, secondLastName, email, birthDate, gender);
	}
}

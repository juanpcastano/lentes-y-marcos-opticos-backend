package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

/**
 * Estado del vínculo con Softix después de verificar el teléfono.
 * `documentHints` solo trae terminaciones enmascaradas; nunca el documento
 * completo ni el identificador del cliente.
 */
public record LinkState(
		Status status,
		String challengeId,
		List<String> documentHints) {

	public enum Status {
		LINKED,
		NEEDS_DOCUMENT,
		NEEDS_CREATE,
		CONTACT_STAFF
	}

	public static LinkState linked() {
		return new LinkState(Status.LINKED, null, List.of());
	}

	public static LinkState needsCreate() {
		return new LinkState(Status.NEEDS_CREATE, null, List.of());
	}

	public static LinkState contactStaff() {
		return new LinkState(Status.CONTACT_STAFF, null, List.of());
	}

	public static LinkState needsDocument(String challengeId, List<String> hints) {
		return new LinkState(Status.NEEDS_DOCUMENT, challengeId, hints);
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PhoneDocumentLookupRequest(
		@NotBlank @Size(max = 2) String documentType,
		@NotBlank
		@Pattern(regexp = "\\d{1,10}", message = "Ingresa el número de documento")
		String documentId) {
}

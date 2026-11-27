package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;

/**
 * Normaliza celulares colombianos a E.164. Solo se aceptan móviles de 10
 * dígitos que empiezan por 3.
 */
@Service
public class PhoneNormalizer {

	private static final String COLOMBIA_PREFIX = "+57";

	public String normalize(String rawPhone) {
		if (rawPhone == null || rawPhone.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Ingresa tu número de celular");
		}

		String digits = rawPhone.replaceAll("\\D", "");
		if (digits.startsWith("00")) {
			digits = digits.substring(2);
		}
		if (digits.startsWith("57") && digits.length() == 12) {
			digits = digits.substring(2);
		}

		if (digits.length() != 10 || digits.charAt(0) != '3') {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Ingresa un celular colombiano válido de 10 dígitos");
		}

		return COLOMBIA_PREFIX + digits;
	}

	public String nationalDigits(String phoneE164) {
		if (phoneE164 == null || !phoneE164.startsWith(COLOMBIA_PREFIX)) {
			throw new IllegalArgumentException("Teléfono no normalizado");
		}
		return phoneE164.substring(COLOMBIA_PREFIX.length());
	}

	public String placeholderEmail(String phoneE164, String domain) {
		return nationalDigits(phoneE164) + "@" + domain;
	}
}

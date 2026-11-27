package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

/**
 * Utilidades para el documento de identidad. Softix usa en algunos casos el
 * documento con un sufijo interno (`800233471-10724`); el número que teclea el
 * usuario es la parte anterior al guion. Nunca se compara un sufijo ni una
 * terminación: el documento debe escribirse completo.
 */
public final class DocumentNumbers {

	private DocumentNumbers() {
	}

	/** Solo los dígitos del documento base, sin sufijo ni formato. */
	public static String baseDigits(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		int dash = value.indexOf('-');
		String base = dash > 0 ? value.substring(0, dash) : value;
		return base.replaceAll("\\D", "");
	}

	/** Enmascara el documento base dejando visibles solo los últimos 4 dígitos. */
	public static String mask(String value) {
		String digits = baseDigits(value);
		if (digits.length() <= 4) {
			return "*".repeat(digits.length());
		}
		return "*".repeat(digits.length() - 4)
				+ digits.substring(digits.length() - 4);
	}
}

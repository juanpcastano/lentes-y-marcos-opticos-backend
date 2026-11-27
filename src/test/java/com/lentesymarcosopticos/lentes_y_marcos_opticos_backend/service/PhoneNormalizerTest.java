package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;

class PhoneNormalizerTest {

	private final PhoneNormalizer normalizer = new PhoneNormalizer();

	@Test
	void normalizaCelularColombianoConPrefijoYFormato() {
		assertThat(normalizer.normalize("300 123 4567")).isEqualTo("+573001234567");
		assertThat(normalizer.normalize("+57 300-123-4567")).isEqualTo("+573001234567");
		assertThat(normalizer.normalize("573001234567")).isEqualTo("+573001234567");
	}

	@Test
	void rechazaFijosYLongitudesInvalidas() {
		assertThatThrownBy(() -> normalizer.normalize("6021234567"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> normalizer.normalize("300123"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> normalizer.normalize(null))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void extraeDigitosNacionalesYArmaEmailDeRelleno() {
		assertThat(normalizer.nationalDigits("+573001234567")).isEqualTo("3001234567");
		assertThat(normalizer.placeholderEmail("+573001234567", "sin-correo.invalid"))
				.isEqualTo("3001234567@sin-correo.invalid");
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentNumbersTest {

	@Test
	void usaElDocumentoBaseAntesDelSufijoDeSoftix() {
		assertThat(DocumentNumbers.baseDigits("800233471-10724")).isEqualTo("800233471");
		assertThat(DocumentNumbers.baseDigits("22.011.069-9")).isEqualTo("22011069");
		assertThat(DocumentNumbers.baseDigits("1000000089")).isEqualTo("1000000089");
		assertThat(DocumentNumbers.baseDigits("0089")).isEqualTo("0089");
		assertThat(DocumentNumbers.baseDigits(null)).isEmpty();
	}

	@Test
	void enmascaraSoloLosDigitosDelDocumentoBase() {
		assertThat(DocumentNumbers.mask("1000000089")).isEqualTo("******0089");
		assertThat(DocumentNumbers.mask("800233471-10724")).isEqualTo("*****3471");
		assertThat(DocumentNumbers.mask("22.011.069-9")).isEqualTo("****1069");
		assertThat(DocumentNumbers.mask("234")).isEqualTo("***");
	}
}

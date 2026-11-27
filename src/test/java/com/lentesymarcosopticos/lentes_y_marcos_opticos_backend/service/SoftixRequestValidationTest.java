package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateCustomerRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileUpdateRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SoftixRequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void rechazaFechaDeNacimientoFuturaAlCrear() {
		var request = new SoftixCreateCustomerRequest(
				"CC", "999", "Ana", null, "Pérez", null, null,
				LocalDate.now().plusDays(1), "Femenino");

		var violations = validator.validate(request);

		assertThat(violations)
				.anyMatch(v -> v.getMessage().contains("no puede ser futura"));
	}

	@Test
	void aceptaFechaDeNacimientoDeHoy() {
		var request = new SoftixCreateCustomerRequest(
				"CC", "999", "Ana", null, "Pérez", null, null,
				LocalDate.now(), "Femenino");

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void rechazaFechaFuturaAlEditar() {
		var request = new SoftixProfileUpdateRequest(
				"Ana", null, "Pérez", null, null,
				LocalDate.now().plusYears(1), "Femenino");

		assertThat(validator.validate(request))
				.anyMatch(v -> v.getMessage().contains("no puede ser futura"));
	}
}

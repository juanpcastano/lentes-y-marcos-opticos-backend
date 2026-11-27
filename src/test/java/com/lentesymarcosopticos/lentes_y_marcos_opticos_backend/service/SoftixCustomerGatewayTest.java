package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateCustomerRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.DocumentTypeItem;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixCustomerIndexRepository;

@ExtendWith(MockitoExtension.class)
class SoftixCustomerGatewayTest {

	private static final String PHONE = "+573001234567";

	@Mock
	private SoftixClient softixClient;
	@Mock
	private SoftixCustomerIndexRepository indexRepository;

	private SoftixCustomerGateway gateway;

	@BeforeEach
	void setUp() {
		gateway = new SoftixCustomerGateway(softixClient, indexRepository,
				new PhoneNormalizer(), "1", "1", "sin-correo.invalid");
	}

	@Test
	void creaConCamposMinimosYCiudadPorDefecto() {
		var request = new SoftixCreateCustomerRequest(
				"CC", "999", "Ana", null, "Pérez", null,
				null, LocalDate.of(1990, 1, 2), "Femenino");

		CustomerCreateRequest body = gateway.buildCreateRequest(request, PHONE);

		assertThat(body.documentId()).isEqualTo("999");
		assertThat(body.mobile()).isEqualTo("3001234567");
		assertThat(body.email()).isEqualTo("3001234567@sin-correo.invalid");
		assertThat(body.address()).isEmpty();
		assertThat(body.city()).isEqualTo("76");
		assertThat(body.locality()).isEqualTo("001");
		assertThat(body.postalCode()).isEmpty();
		assertThat(body.maritalStatus()).isEmpty();
		assertThat(body.occupation()).isEmpty();
		assertThat(body.authorization()).isEqualTo(1);
		assertThat(body.bloodType()).isEqualTo("NE");
	}

	@Test
	void actualizaConservandoTelefonoYCamposNoEditables() {
		CustomerResponse current = customer();
		var request = new SoftixProfileUpdateRequest(
				"Ana María", null, "Pérez", null, " ",
				LocalDate.of(1990, 1, 2), "Femenino");

		CustomerCreateRequest body = gateway.merge(current, request, PHONE, true);

		assertThat(body.firstName()).isEqualTo("Ana María");
		assertThat(body.documentId()).isEqualTo(current.documentId());
		assertThat(body.mobile()).isEqualTo(current.mobile());
		assertThat(body.address()).isEqualTo(current.address());
		assertThat(body.city()).isEqualTo("76");
		assertThat(body.locality()).isEqualTo("001");
		assertThat(body.email()).isEqualTo("3001234567@sin-correo.invalid");
		assertThat(body.authorization()).isEqualTo(1);
	}

	@Test
	void noAutorizaSiNoHayConsentimiento() {
		CustomerResponse current = customer();
		var request = new SoftixProfileUpdateRequest(
				"Ana", null, "Pérez", null, "ana@example.com",
				null, "Femenino");

		CustomerCreateRequest body = gateway.merge(current, request, PHONE, false);

		assertThat(body.authorization()).isEqualTo(0);
	}

	@Test
	void vinculaFichaSinTelefonoYUsaElTelefonoVerificado() {
		CustomerResponse current = customer("", "");
		var request = new SoftixCreateCustomerRequest(
				"CC", "1000000199", "Ana María", null, "Pérez", null,
				null, LocalDate.of(1990, 1, 2), "Femenino");

		CustomerCreateRequest body = gateway.mergeForLink(current, request, PHONE);

		assertThat(body.documentId()).isEqualTo(current.documentId());
		assertThat(body.firstName()).isEqualTo("Ana María");
		assertThat(body.mobile()).isEqualTo("3001234567");
		assertThat(body.phone()).isEmpty();
		assertThat(body.address()).isEqualTo(current.address());
		assertThat(body.authorization()).isEqualTo(1);
		assertThat(body.bloodType()).isEqualTo("NE");
		assertThat(body.personType()).isEqualTo("Natural");
		assertThat(body.regime()).isEqualTo("49");
		assertThat(body.country()).isEqualTo("CO");
	}

	@Test
	void soloVinculaFichasSinTelefonoOMismoTelefono() {
		assertThat(gateway.phoneMatches(customer("", ""), PHONE)).isTrue();
		assertThat(gateway.phoneMatches(customer("", "5555555"), PHONE)).isTrue();
		assertThat(gateway.phoneMatches(customer("0", ""), PHONE)).isTrue();
		assertThat(gateway.phoneMatches(customer("3001234567", ""), PHONE)).isTrue();
		assertThat(gateway.phoneMatches(customer("3009999999", ""), PHONE)).isFalse();
		assertThat(gateway.phoneMatches(customer("3001234567", "3009999999"), PHONE)).isFalse();
	}

	@Test
	void aceptaSoloLosTiposDeDocumentoDelAutoservicio() {
		assertThat(gateway.isSupportedDocumentType("cc")).isTrue();
		assertThat(gateway.isSupportedDocumentType("CE")).isTrue();
		assertThat(gateway.isSupportedDocumentType("NT")).isFalse();
		assertThat(gateway.isSupportedDocumentType(null)).isFalse();
	}

	@Test
	void exigeElDocumentoCompletoYNoAceptaTerminaciones() {
		assertThat(gateway.findMatch(List.of("1000000045", "1000000089"), "1000000089"))
				.isEqualTo("1000000089");
		assertThat(gateway.findMatch(List.of("1000000045", "1000000089"), "0089")).isNull();
		assertThat(gateway.findMatch(List.of("1000000045"), "0045")).isNull();
		assertThat(gateway.findMatch(List.of("800233471-10724"), "800233471"))
				.isEqualTo("800233471-10724");
		assertThat(gateway.findMatch(List.of("22.011.069-9"), "22011069"))
				.isEqualTo("22.011.069-9");
		assertThatThrownBy(() -> gateway.findMatch(
				List.of("1000000089-1", "1000000089-2"), "1000000089"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void enmascaraElDocumentoBaseYNoElSufijo() {
		assertThat(gateway.maskDocument("1000000089")).isEqualTo("******0089");
		assertThat(gateway.maskDocument("800233471-10724")).isEqualTo("*****3471");
		assertThat(gateway.maskDocument("22.011.069-9")).isEqualTo("****1069");
	}

	@Test
	void validaDisponibilidadDelDocumento() {
		when(softixClient.findCustomer("999")).thenReturn(customer());
		assertThat(gateway.isDocumentAvailable("999")).isFalse();
	}

	@Test
	void filtraYdeduplicaTiposDeDocumento() {
		when(softixClient.listDocumentTypes()).thenReturn(List.of(
				new DocumentTypeItem("CC", "Cédula de ciudadanía", 5),
				new DocumentTypeItem("CC", "Cédula de ciudadanía", 5),
				new DocumentTypeItem("RC", "Registro civil", 11),
				new DocumentTypeItem("PA", "Pasaporte", 41),
				new DocumentTypeItem("TI", "Tarjeta de identidad", 12),
				new DocumentTypeItem("CE", "Cédula de extranjería", 22)));

		var types = gateway.documentTypes();

		assertThat(types).extracting(com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.DocumentTypeResponse::id)
				.containsExactly("CC", "CE", "TI", "PA");
		assertThat(types).extracting(com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.DocumentTypeResponse::name)
				.containsExactly("Cédula de ciudadanía", "Cédula de extranjería",
						"Tarjeta de identidad", "Pasaporte");
	}

	private CustomerResponse customer() {
		return customer("3001234567", "3001234567");
	}

	private CustomerResponse customer(String mobile, String phone) {
		return new CustomerResponse(
				"1000000199", "CC", "Ana", null, "Pérez", null,
				mobile, phone, "ana@example.com", "Calle 1",
				LocalDateTime.of(1990, 1, 2, 0, 0), "76", "001", "Femenino");
	}
}

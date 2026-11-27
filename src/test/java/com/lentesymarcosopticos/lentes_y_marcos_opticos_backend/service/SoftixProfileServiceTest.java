package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.DocumentTypeResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;

@ExtendWith(MockitoExtension.class)
class SoftixProfileServiceTest {

	@Mock
	private AuthService authService;
	@Mock
	private SoftixCustomerGateway gateway;
	@Mock
	private SoftixWriteLimiter writeLimiter;

	private SoftixProfileService service;

	@BeforeEach
	void setUp() {
		service = new SoftixProfileService(authService, gateway, writeLimiter);
	}

	@Test
	void actualizaSoloLaListaBlancaDeUnUsuarioVinculado() {
		User user = linkedUser();
		CustomerResponse current = customer();
		CustomerCreateRequest body = CustomerCreateRequest.builder()
				.documentId(current.documentId())
				.build();
		var expected = new com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileResponse(
				"CC", "******0199", "Ana María", null, "Pérez", null,
				"3001234567@sin-correo.invalid",
				LocalDate.of(1990, 1, 2), "Femenino", "76", "001",
				"", "", "");
		SoftixProfileUpdateRequest request = request();

		when(authService.currentUser()).thenReturn(user);
		when(gateway.fetchCustomer(user.getSoftixClientId())).thenReturn(current);
		when(gateway.merge(current, request, user.getPhoneE164(), true)).thenReturn(body);
		when(gateway.toProfile(body)).thenReturn(expected);

		var response = service.updateProfile(request);

		assertThat(response).isEqualTo(expected);
		verify(gateway).updateCustomer(current.documentId(), body);
		verify(writeLimiter).checkAndRecord(user.getPhoneE164(),
				SoftixWriteLimiter.OPERATION_UPDATE);
	}

	@Test
	void rechazaEdicionSiLaCuentaNoEstaVinculada() {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setPhoneE164("+573001234567");
		user.setPhoneVerifiedAt(java.time.LocalDateTime.now());
		when(authService.currentUser()).thenReturn(user);

		assertThatThrownBy(() -> service.updateProfile(request()))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void delegaElCatalogoDeTiposDeDocumento() {
		var types = List.of(new DocumentTypeResponse("CC", "Cédula", 1));
		when(gateway.documentTypes()).thenReturn(types);

		assertThat(service.documentTypes()).isEqualTo(types);
	}

	private SoftixProfileUpdateRequest request() {
		return new SoftixProfileUpdateRequest(
				"Ana María", null, "Pérez", null, " ",
				LocalDate.of(1990, 1, 2), "Femenino");
	}

	private User linkedUser() {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setPhoneE164("+573001234567");
		user.setPhoneVerifiedAt(java.time.LocalDateTime.now());
		user.setSoftixClientId("1000000199");
		user.setSoftixLinkedAt(java.time.LocalDateTime.now());
		user.setConsentAt(java.time.LocalDateTime.now());
		return user;
	}

	private CustomerResponse customer() {
		return new CustomerResponse(
				"1000000199", "CC", "Ana", null, "Pérez", null,
				"3001234567", "3001234567", "ana@example.com", "Calle 1",
				java.time.LocalDateTime.of(1990, 1, 2, 0, 0), "76", "001", "Femenino");
	}
}

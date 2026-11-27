package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneChallengeConfirmRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneDocumentLookupRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.RegistrationProfileResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateCustomerRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.UserResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.PhoneVerification;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixLinkChallenge;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.PhoneVerificationRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixLinkChallengeRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

	private static final String PHONE = "+573001234567";
	private static final String TOKEN = "registration-token";

	@Mock
	private PhoneVerificationRepository verificationRepository;
	@Mock
	private SoftixLinkChallengeRepository challengeRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private SoftixCustomerGateway gateway;
	@Mock
	private SessionService sessionService;
	@Mock
	private JwtService jwtService;
	@Mock
	private AuthService authService;
	@Mock
	private SoftixWriteLimiter writeLimiter;

	private RegistrationService service;
	private PhoneVerification verification;

	@BeforeEach
	void setUp() {
		service = new RegistrationService(verificationRepository, challengeRepository,
				userRepository, gateway, sessionService, jwtService, authService,
				writeLimiter);
		verification = new PhoneVerification();
		verification.setId(UUID.randomUUID());
		verification.setPhoneE164(PHONE);
		verification.setConsentVersion("2026-11");
		verification.setConsentAt(LocalDateTime.now());
		verification.setConsentIp("1.2.3.4");
		verification.setCreatedAt(LocalDateTime.now());
		verification.setExpiresAt(LocalDateTime.now().plusMinutes(10));
		verification.setConsumed(false);

		lenient().when(jwtService.isRegistrationToken(TOKEN)).thenReturn(true);
		lenient().when(jwtService.extractRegistrationId(TOKEN)).thenReturn(verification.getId());
		lenient().when(jwtService.extractPhone(TOKEN)).thenReturn(PHONE);
		lenient().when(verificationRepository.findByIdAndConsumedFalseAndExpiresAtAfter(
				eq(verification.getId()), any())).thenReturn(Optional.of(verification));
		lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			if (user.getId() == null) {
				user.setId(UUID.randomUUID());
			}
			return user;
		});
		lenient().when(sessionService.issue(any(User.class)))
				.thenReturn(new SessionService.IssuedSession("access", "refresh"));
		lenient().when(authService.toUserResponse(any(User.class)))
				.thenAnswer(invocation -> {
					User user = invocation.getArgument(0);
					return new UserResponse(user.getId(), user.getPhoneE164(), true, true, false);
				});
	}

	@Test
	void confirmaElDocumentoYDevuelveDatosSinCrearLaCuenta() {
		UUID challengeId = UUID.randomUUID();
		SoftixLinkChallenge challenge = new SoftixLinkChallenge();
		challenge.setId(challengeId);
		challenge.setPhoneVerificationId(verification.getId());
		challenge.setPhoneE164(PHONE);
		challenge.setCandidateClientIds("1000000045,1000000089");
		challenge.setAttempts(0);
		challenge.setConsumed(false);
		challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
		CustomerResponse customer = customer("1000000089");

		when(challengeRepository.findByIdAndConsumedFalseAndExpiresAtAfter(eq(challengeId), any()))
				.thenReturn(Optional.of(challenge));
		when(gateway.findMatch(any(), eq("1000000089"))).thenReturn("1000000089");
		when(gateway.fetchCustomer("1000000089")).thenReturn(customer);
		when(gateway.isSupportedDocumentType("CC")).thenReturn(true);
		when(gateway.phoneMatches(customer, PHONE)).thenReturn(true);

		var profile = service.confirmLinkDocument(TOKEN,
				new PhoneChallengeConfirmRequest(challengeId.toString(), "1000000089"));

		assertThat(profile.status()).isEqualTo(RegistrationProfileResponse.Status.LINK);
		assertThat(profile.documentId()).isEqualTo("1000000089");
		assertThat(profile.firstName()).isEqualTo("Ana");
		assertThat(profile.birthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
		verify(userRepository, never()).save(any(User.class));
		assertThat(verification.getConsumed()).isFalse();
		verify(gateway, never()).upsertIndex(any(), any());
	}

	@Test
	void vinculaLaFichaYcreaLaCuentaSoloAlConfirmarElFormulario() {
		SoftixCreateCustomerRequest request = new SoftixCreateCustomerRequest(
				"CC", "1000000089", "Ana", null, "Pérez", null,
				null, LocalDate.of(1990, 1, 2), "Femenino");
		CustomerResponse current = customer("1000000089");
		CustomerCreateRequest body = CustomerCreateRequest.builder()
				.documentId("1000000089")
				.build();

		when(gateway.fetchCustomer("1000000089")).thenReturn(current);
		when(gateway.isSupportedDocumentType("CC")).thenReturn(true);
		when(gateway.phoneMatches(current, PHONE)).thenReturn(true);
		when(gateway.mergeForLink(current, request, PHONE)).thenReturn(body);

		var completed = service.completeLinkProfile(TOKEN, request);

		assertThat(completed.response().registrationPending()).isFalse();
		assertThat(completed.response().accessToken()).isEqualTo("access");
		assertThat(completed.refreshToken()).isEqualTo("refresh");
		verify(gateway).updateCustomer("1000000089", body);
		verify(writeLimiter).checkAndRecord(PHONE, SoftixWriteLimiter.OPERATION_LINK);
		verify(userRepository).save(any(User.class));
		assertThat(verification.getConsumed()).isTrue();
		verify(gateway).upsertIndex(PHONE, "1000000089");
	}

	@Test
	void buscaUnDocumentoExistenteYDevuelveLosDatosParaPrecargar() {
		CustomerResponse customer = customer("1000000089");
		when(gateway.fetchCustomer("1000000089")).thenReturn(customer);
		when(gateway.isSupportedDocumentType("CC")).thenReturn(true);
		when(gateway.phoneMatches(customer, PHONE)).thenReturn(true);

		var profile = service.lookupDocument(TOKEN,
				new PhoneDocumentLookupRequest("CC", "1000000089"));

		assertThat(profile.status()).isEqualTo(RegistrationProfileResponse.Status.LINK);
		assertThat(profile.documentType()).isEqualTo("CC");
		assertThat(profile.documentId()).isEqualTo("1000000089");
		assertThat(profile.lastName()).isEqualTo("Pérez");
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void cuentaLasConsultasDeDocumento() {
		CustomerResponse customer = customer("1000000089");
		when(gateway.fetchCustomer("1000000089")).thenReturn(customer);
		when(gateway.isSupportedDocumentType("CC")).thenReturn(true);
		when(gateway.phoneMatches(customer, PHONE)).thenReturn(true);

		service.lookupDocument(TOKEN,
				new PhoneDocumentLookupRequest("CC", "1000000089"));

		assertThat(verification.getDocumentLookupAttempts()).isEqualTo(1);
		verify(verificationRepository).save(verification);
	}

	@Test
	void limitaLasConsultasDeDocumentoPorVerificacion() {
		verification.setDocumentLookupAttempts(5);

		assertThatThrownBy(() -> service.lookupDocument(TOKEN,
				new PhoneDocumentLookupRequest("CC", "1000000089")))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("demasiadas consultas");
		verify(gateway, never()).fetchCustomer(any());
	}

	@Test
	void buscaUnDocumentoInexistenteYDejaElFormularioEnCreacion() {
		when(gateway.fetchCustomer("12345"))
				.thenThrow(new ApiException(HttpStatus.NOT_FOUND, "No encontramos tus datos."));

		var profile = service.lookupDocument(TOKEN,
				new PhoneDocumentLookupRequest("CC", "12345"));

		assertThat(profile.status()).isEqualTo(RegistrationProfileResponse.Status.CREATE);
		assertThat(profile.documentId()).isEqualTo("12345");
		assertThat(profile.firstName()).isNull();
	}

	@Test
	void noVinculaUnaFichaQueYaTieneOtroTelefono() {
		CustomerResponse customer = customer("1000000089");
		when(gateway.fetchCustomer("1000000089")).thenReturn(customer);
		when(gateway.isSupportedDocumentType("CC")).thenReturn(true);
		when(gateway.phoneMatches(customer, PHONE)).thenReturn(false);

		assertThatThrownBy(() -> service.lookupDocument(TOKEN,
				new PhoneDocumentLookupRequest("CC", "1000000089")))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("otro número de teléfono");
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void noVinculaUnaFichaYaVinculadaAOtraCuenta() {
		CustomerResponse customer = customer("1000000089");
		when(gateway.fetchCustomer("1000000089")).thenReturn(customer);
		when(userRepository.existsBySoftixClientId("1000000089")).thenReturn(true);

		assertThatThrownBy(() -> service.lookupDocument(TOKEN,
				new PhoneDocumentLookupRequest("CC", "1000000089")))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("vinculado a otra cuenta");
	}

	@Test
	void creaLaCuentaSoloDespuesDeCrearLaFicha() {
		SoftixCreateCustomerRequest request = new SoftixCreateCustomerRequest(
				"CC", "999", "Ana", null, "Pérez", null,
				null, LocalDate.of(1990, 1, 2), "Femenino");
		CustomerCreateRequest body = CustomerCreateRequest.builder()
				.documentId("999")
				.build();

		when(gateway.hasIndexForPhone(PHONE)).thenReturn(false);
		when(gateway.isDocumentAvailable("999")).thenReturn(true);
		when(gateway.buildCreateRequest(request, PHONE)).thenReturn(body);

		var completed = service.completeCreate(TOKEN, request);

		assertThat(completed.response().accessToken()).isEqualTo("access");
		verify(gateway).createCustomer(body);
		verify(writeLimiter).checkAndRecord(PHONE, SoftixWriteLimiter.OPERATION_CREATE);
		verify(userRepository).save(any(User.class));
		assertThat(verification.getConsumed()).isTrue();
		verify(gateway).upsertIndex(PHONE, "999");
	}

	@Test
	void noCreaCuentaSiLaVerificacionNoEsValida() {
		when(jwtService.isRegistrationToken("otro")).thenReturn(false);

		assertThatThrownBy(() -> service.completeCreate("otro",
				new SoftixCreateCustomerRequest("CC", "999", "Ana", null, "Pérez",
						null, null, LocalDate.of(1990, 1, 2), "Femenino")))
				.isInstanceOf(ApiException.class);
		verify(userRepository, never()).save(any());
	}

	private CustomerResponse customer(String documentId) {
		return new CustomerResponse(
				documentId, "CC", "Ana", null, "Pérez", null,
				"3001234567", "3001234567", "ana@example.com", null,
				LocalDateTime.of(1990, 1, 2, 0, 0), "76", "001", "Femenino");
	}
}

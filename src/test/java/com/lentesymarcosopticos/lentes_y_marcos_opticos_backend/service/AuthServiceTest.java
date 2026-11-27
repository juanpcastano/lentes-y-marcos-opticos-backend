package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LinkState;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneVerifyRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.PhoneVerification;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixCustomerIndex;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixLinkChallenge;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.PhoneVerificationRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixCustomerIndexRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixLinkChallengeRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String PHONE = "3001234567";
	private static final String E164 = "+573001234567";

	@Mock
	private UserRepository userRepository;
	@Mock
	private PhoneVerificationRepository verificationRepository;
	@Mock
	private SoftixCustomerIndexRepository indexRepository;
	@Mock
	private SoftixLinkChallengeRepository challengeRepository;
	@Mock
	private OtpService otpService;
	@Mock
	private JwtService jwtService;
	@Mock
	private SessionService sessionService;
	@Mock
	private CaptchaService captchaService;

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(userRepository, verificationRepository, indexRepository,
				challengeRepository, otpService, jwtService, sessionService,
				new PhoneNormalizer(), captchaService, "2026-11");
		lenient().when(verificationRepository.save(any(PhoneVerification.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(jwtService.generateRegistrationToken(any(), eq(E164)))
				.thenReturn("registration-token");
	}

	@Test
	void verificaOtpNuevoSinCrearUsuario() {
		when(otpService.verifyAndConsume(E164, OtpService.PURPOSE_LOGIN, "123456"))
				.thenReturn(true);
		when(userRepository.findByPhoneE164(E164)).thenReturn(Optional.empty());
		when(indexRepository.findByPhoneE164OrderBySoftixClientId(E164)).thenReturn(List.of());

		AuthService.VerifyResult result = service.verify(
				new PhoneVerifyRequest(PHONE, "123456", true), "1.2.3.4");

		assertThat(result.response().registrationPending()).isTrue();
		assertThat(result.response().accessToken()).isNull();
		assertThat(result.response().user()).isNull();
		assertThat(result.response().link().status()).isEqualTo(LinkState.Status.NEEDS_CREATE);
		assertThat(result.registrationToken()).isEqualTo("registration-token");
		verify(userRepository, never()).save(any());
		verify(verificationRepository).save(any(PhoneVerification.class));
	}

	@Test
	void usuarioVinculadoRecibeSesionDirecta() {
		User user = linkedUser();
		when(otpService.verifyAndConsume(E164, OtpService.PURPOSE_LOGIN, "123456"))
				.thenReturn(true);
		when(userRepository.findByPhoneE164(E164)).thenReturn(Optional.of(user));
		when(userRepository.save(user)).thenReturn(user);
		when(sessionService.issue(user))
				.thenReturn(new SessionService.IssuedSession("access", "refresh"));

		AuthService.VerifyResult result = service.verify(
				new PhoneVerifyRequest(PHONE, "123456", false), "1.2.3.4");

		assertThat(result.response().registrationPending()).isFalse();
		assertThat(result.response().accessToken()).isEqualTo("access");
		assertThat(result.response().link().status()).isEqualTo(LinkState.Status.LINKED);
		assertThat(result.registrationToken()).isNull();
		verify(verificationRepository, never()).save(any());
	}

	@Test
	void exigeConsentimientoCuandoLaCuentaEsNueva() {
		when(otpService.verifyAndConsume(E164, OtpService.PURPOSE_LOGIN, "123456"))
				.thenReturn(true);
		when(userRepository.findByPhoneE164(E164)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.verify(
				new PhoneVerifyRequest(PHONE, "123456", false), "1.2.3.4"))
				.isInstanceOf(ApiException.class);
		verify(userRepository, never()).save(any());
		verify(verificationRepository, never()).save(any());
	}

	@Test
	void conVariasFichasCreaRetoYDevuelveSoloTerminaciones() {
		when(otpService.verifyAndConsume(E164, OtpService.PURPOSE_LOGIN, "123456"))
				.thenReturn(true);
		when(userRepository.findByPhoneE164(E164)).thenReturn(Optional.empty());
		when(indexRepository.findByPhoneE164OrderBySoftixClientId(E164))
				.thenReturn(List.of(index("1000000045"), index("1000000089")));
		when(challengeRepository.findByPhoneE164AndCreatedAtAfter(eq(E164), any()))
				.thenReturn(List.of());
		when(challengeRepository
				.findFirstByPhoneVerificationIdAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
						any(), any()))
				.thenReturn(Optional.empty());
		when(challengeRepository.save(any(SoftixLinkChallenge.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		AuthService.VerifyResult result = service.verify(
				new PhoneVerifyRequest(PHONE, "123456", true), "1.2.3.4");

		LinkState link = result.response().link();
		assertThat(link.status()).isEqualTo(LinkState.Status.NEEDS_DOCUMENT);
		assertThat(link.challengeId()).isNotBlank();
		assertThat(link.documentHints()).containsExactly("******0045", "******0089");
		assertThat(link.documentHints()).noneMatch(hint -> hint.contains("10000000"));
	}

	private User linkedUser() {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setPhoneE164(E164);
		user.setPhoneVerifiedAt(LocalDateTime.now());
		user.setSoftixClientId("1000000045");
		user.setSoftixLinkedAt(LocalDateTime.now());
		return user;
	}

	private SoftixCustomerIndex index(String clientId) {
		SoftixCustomerIndex row = new SoftixCustomerIndex();
		row.setPhoneE164(E164);
		row.setSoftixClientId(clientId);
		row.setSyncedAt(LocalDateTime.now());
		return row;
	}
}

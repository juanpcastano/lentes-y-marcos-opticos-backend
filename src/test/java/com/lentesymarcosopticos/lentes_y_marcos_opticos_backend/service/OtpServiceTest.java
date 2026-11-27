package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthCode;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.AuthCodeRepository;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	private static final String PHONE = "+573001234567";

	@Mock
	private AuthCodeRepository repository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private WhatsAppService whatsAppService;
	@Mock
	private OtpAbuseMonitor abuseMonitor;

	@BeforeEach
	void setUp() {
		lenient().when(abuseMonitor.dailyLimit()).thenReturn(500);
		lenient().when(abuseMonitor.spikeWindowMinutes()).thenReturn(5);
	}

	private OtpService service() {
		return new OtpService(repository, passwordEncoder, whatsAppService, abuseMonitor);
	}

	@Test
	void guardaCodigoHasheadoYLoEnviaPorWhatsApp() {
		when(repository.findByIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
				PHONE, OtpService.PURPOSE_LOGIN)).thenReturn(List.of());
		when(repository.countByIdentifierAndPurposeAndCreatedAtAfter(
				eq(PHONE), eq(OtpService.PURPOSE_LOGIN), any())).thenReturn(0L);
		when(repository.countByIdentifierAndCreatedAtAfter(eq(PHONE), any())).thenReturn(0L);
		when(repository.countByRequestIpAndCreatedAtAfter(eq("1.2.3.4"), any())).thenReturn(0L);
		when(passwordEncoder.encode(anyString())).thenReturn("hash");

		service().requestCode(PHONE, OtpService.PURPOSE_LOGIN, "1.2.3.4", "agente", "device-1");

		ArgumentCaptor<AuthCode> codeCaptor = ArgumentCaptor.forClass(AuthCode.class);
		verify(repository).save(codeCaptor.capture());
		AuthCode saved = codeCaptor.getValue();
		assertThat(saved.getIdentifier()).isEqualTo(PHONE);
		assertThat(saved.getCodeHash()).isEqualTo("hash");
		assertThat(saved.getChannel()).isEqualTo("WHATSAPP");
		assertThat(saved.getDeviceId()).isEqualTo("device-1");
		assertThat(saved.getConsumed()).isFalse();
		assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());

		ArgumentCaptor<String> codeValue = ArgumentCaptor.forClass(String.class);
		verify(whatsAppService).sendOtp(eq(PHONE), codeValue.capture());
		assertThat(codeValue.getValue()).matches("\\d{6}");
	}

	@Test
	void respetaEnfriamientoDeSesentaSegundos() {
		AuthCode active = new AuthCode();
		active.setCreatedAt(LocalDateTime.now().minusSeconds(10));
		when(repository.findByIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
				PHONE, OtpService.PURPOSE_LOGIN)).thenReturn(List.of(active));

		assertThatThrownBy(() -> service().requestCode(
				PHONE, OtpService.PURPOSE_LOGIN, "1.2.3.4", "agente", null))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

		verify(whatsAppService, never()).sendOtp(any(), any());
	}

	@Test
	void bloqueaElEnvioCuandoSeAlcanzaElTopeDiarioDeWhatsApp() {
		when(repository.countByChannelAndCreatedAtAfter(eq("WHATSAPP"), any()))
				.thenReturn(500L);

		assertThatThrownBy(() -> service().requestCode(
				PHONE, OtpService.PURPOSE_LOGIN, "1.2.3.4", "agente", "device-1"))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

		verify(whatsAppService, never()).sendOtp(any(), any());
	}

	@Test
	void bloqueaElEnvioCuandoElDispositivoSuperaSuLimite() {
		when(repository.findByIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
				PHONE, OtpService.PURPOSE_LOGIN)).thenReturn(List.of());
		when(repository.countByDeviceIdAndCreatedAtAfter(eq("device-1"), any()))
				.thenReturn(10L);

		assertThatThrownBy(() -> service().requestCode(
				PHONE, OtpService.PURPOSE_LOGIN, "1.2.3.4", "agente", "device-1"))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

		verify(whatsAppService, never()).sendOtp(any(), any());
	}

	@Test
	void cuentaIntentosFallidosYConsumeAlLlegarAlMaximo() {
		AuthCode active = new AuthCode();
		active.setCodeHash("hash");
		active.setAttempts(4);
		active.setConsumed(false);
		when(repository.findByIdentifierAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
				eq(PHONE), eq(OtpService.PURPOSE_LOGIN), any())).thenReturn(List.of(active));
		when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

		assertThat(service().verifyAndConsume(PHONE, OtpService.PURPOSE_LOGIN, "000000"))
				.isFalse();
		assertThat(active.getAttempts()).isEqualTo(5);
		assertThat(active.getConsumed()).isTrue();
		verify(repository).saveAll(List.of(active));
	}
}

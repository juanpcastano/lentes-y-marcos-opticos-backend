package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthCode;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.AuthCodeRepository;

import lombok.AllArgsConstructor;

/**
 * Códigos de un solo uso enviados por WhatsApp. Los códigos se guardan
 * hasheados y nunca se registran en logs de producción.
 */
@Service
@AllArgsConstructor
public class OtpService {

	public static final String PURPOSE_LOGIN = "LOGIN";

	private static final int CODE_LENGTH = 6;
	private static final int EXPIRY_MINUTES = 10;
	private static final int COOLDOWN_SECONDS = 60;
	private static final int MAX_CODES_PER_HOUR = 5;
	private static final int MAX_CODES_PER_DAY = 10;
	private static final int MAX_CODES_PER_IP_HOUR = 10;
	private static final int MAX_CODES_PER_DEVICE_HOUR = 10;
	private static final int MAX_ATTEMPTS = 5;

	private final AuthCodeRepository authCodeRepository;
	private final PasswordEncoder passwordEncoder;
	private final WhatsAppService whatsAppService;
	private final OtpAbuseMonitor abuseMonitor;

	public void requestCode(String phoneE164, String purpose, String requestIp,
			String userAgent, String deviceId) {
		LocalDateTime now = LocalDateTime.now();
		long todayCount = authCodeRepository.countByChannelAndCreatedAtAfter(
				"WHATSAPP", LocalDate.now().atStartOfDay());
		if (todayCount >= abuseMonitor.dailyLimit()) {
			abuseMonitor.dailyLimitReached(todayCount);
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"No podemos enviar más códigos hoy. Intenta de nuevo mañana.");
		}

		List<AuthCode> active = authCodeRepository
				.findByIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(phoneE164, purpose);

		if (!active.isEmpty()) {
			AuthCode latest = active.get(0);
			long elapsedSeconds = Duration.between(latest.getCreatedAt(), now).getSeconds();
			if (elapsedSeconds < COOLDOWN_SECONDS) {
				long waitSeconds = COOLDOWN_SECONDS - elapsedSeconds;
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
						"Espera " + waitSeconds + " segundos antes de pedir otro código");
			}
		}

		if (authCodeRepository.countByIdentifierAndPurposeAndCreatedAtAfter(
				phoneE164, purpose, now.minusHours(1)) >= MAX_CODES_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Has solicitado demasiados códigos. Intenta de nuevo en una hora.");
		}

		if (authCodeRepository.countByIdentifierAndCreatedAtAfter(
				phoneE164, now.minusDays(1)) >= MAX_CODES_PER_DAY) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Has solicitado demasiados códigos hoy. Intenta de nuevo mañana.");
		}

		if (requestIp != null && !requestIp.isBlank()
				&& authCodeRepository.countByRequestIpAndCreatedAtAfter(
						requestIp, now.minusHours(1)) >= MAX_CODES_PER_IP_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Demasiadas solicitudes desde esta conexión. Intenta más tarde.");
		}

		if (deviceId != null && !deviceId.isBlank()
				&& authCodeRepository.countByDeviceIdAndCreatedAtAfter(
						deviceId, now.minusHours(1)) >= MAX_CODES_PER_DEVICE_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Demasiadas solicitudes desde este dispositivo. Intenta más tarde.");
		}

		for (AuthCode authCode : active) {
			authCode.setConsumed(true);
		}
		authCodeRepository.saveAll(active);

		String code = generateCode();
		AuthCode authCode = new AuthCode();
		authCode.setIdentifier(phoneE164);
		authCode.setChannel("WHATSAPP");
		authCode.setPurpose(purpose);
		authCode.setCodeHash(passwordEncoder.encode(code));
		authCode.setExpiresAt(now.plusMinutes(EXPIRY_MINUTES));
		authCode.setConsumed(false);
		authCode.setAttempts(0);
		authCode.setRequestIp(truncate(requestIp, 45));
		authCode.setUserAgent(truncate(userAgent, 255));
		authCode.setDeviceId(truncate(deviceId, 64));
		authCodeRepository.save(authCode);

		abuseMonitor.dailyUsageAlert(todayCount + 1);
		long recent = authCodeRepository.countByChannelAndCreatedAtAfter(
				"WHATSAPP", now.minusMinutes(abuseMonitor.spikeWindowMinutes()));
		abuseMonitor.spikeDetected(recent);

		whatsAppService.sendOtp(phoneE164, code);
	}

	public boolean verifyAndConsume(String phoneE164, String purpose, String code) {
		AuthCode match = findMatchingCode(phoneE164, purpose, code);
		if (match == null) {
			return false;
		}
		match.setConsumed(true);
		authCodeRepository.save(match);
		return true;
	}

	private AuthCode findMatchingCode(String phoneE164, String purpose, String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		List<AuthCode> codes = authCodeRepository
				.findByIdentifierAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
						phoneE164, purpose, LocalDateTime.now());

		for (AuthCode authCode : codes) {
			if (passwordEncoder.matches(code, authCode.getCodeHash())) {
				return authCode;
			}
		}

		registerFailedAttempts(codes);
		return null;
	}

	private void registerFailedAttempts(List<AuthCode> codes) {
		for (AuthCode authCode : codes) {
			authCode.setAttempts(authCode.getAttempts() + 1);
			if (authCode.getAttempts() >= MAX_ATTEMPTS) {
				authCode.setConsumed(true);
			}
		}
		authCodeRepository.saveAll(codes);
	}

	private String generateCode() {
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			sb.append(random.nextInt(10));
		}
		return sb.toString();
	}

	private String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}

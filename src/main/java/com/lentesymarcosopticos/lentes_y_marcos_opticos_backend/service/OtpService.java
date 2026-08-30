package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CodeStatusResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthCode;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.AuthCodeRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class OtpService {

	private static final int CODE_LENGTH = 6;
	private static final int EXPIRY_MINUTES = 10;
	private static final int COOLDOWN_SECONDS = 60;
	private static final int MAX_CODES_PER_HOUR = 5;
	private static final int MAX_CODES_PER_HOUR_GLOBAL = 8;
	private static final int MAX_ATTEMPTS = 5;

	private final AuthCodeRepository authCodeRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;

	public void generateAndSend(String email, String purpose) {
		List<AuthCode> active = authCodeRepository
				.findByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, purpose);

		if (!active.isEmpty()) {
			AuthCode latest = active.get(0);
			long elapsedSeconds = Duration.between(latest.getCreatedAt(), LocalDateTime.now()).getSeconds();
			if (elapsedSeconds < COOLDOWN_SECONDS) {
				long waitSeconds = COOLDOWN_SECONDS - elapsedSeconds;
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
						"Espera " + waitSeconds + " segundos antes de pedir otro código");
			}
		}

		if (authCodeRepository.countByEmailAndPurposeAndCreatedAtAfter(
				email, purpose, LocalDateTime.now().minusHours(1)) >= MAX_CODES_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Has solicitado demasiados c\u00f3digos de este tipo. Intenta de nuevo en una hora.");
		}

		if (authCodeRepository.countByEmailAndCreatedAtAfter(
				email, LocalDateTime.now().minusHours(1)) >= MAX_CODES_PER_HOUR_GLOBAL) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Has solicitado demasiados c\u00f3digos. Intenta de nuevo en una hora.");
		}

		for (AuthCode authCode : active) {
			authCode.setConsumed(true);
		}
		authCodeRepository.saveAll(active);

		String code = generateCode();
		String hash = passwordEncoder.encode(code);

		AuthCode authCode = new AuthCode();
		authCode.setEmail(email);
		authCode.setCodeHash(hash);
		authCode.setPurpose(purpose);
		authCode.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
		authCode.setConsumed(false);
		authCode.setAttempts(0);
		authCodeRepository.save(authCode);

		emailService.sendOtpCode(email, code, purpose);
	}

	public boolean verifyAndConsume(String email, String code, String purpose) {
		AuthCode match = findMatchingCode(email, code, purpose);
		if (match == null) {
			return false;
		}
		match.setConsumed(true);
		authCodeRepository.save(match);
		return true;
	}

	public boolean verifyOnly(String email, String code, String purpose) {
		return findMatchingCode(email, code, purpose) != null;
	}

	public CodeStatusResponse getCodeStatus(String email, String purpose) {
		LocalDateTime now = LocalDateTime.now();
		List<AuthCode> unconsumed = authCodeRepository
				.findByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, purpose);

		boolean active = unconsumed.stream().anyMatch(c -> c.getExpiresAt().isAfter(now));

		long cooldownSeconds = 0;
		if (!unconsumed.isEmpty()) {
			AuthCode latest = unconsumed.get(0);
			long elapsedSeconds = Duration.between(latest.getCreatedAt(), now).getSeconds();
			if (elapsedSeconds < COOLDOWN_SECONDS) {
				cooldownSeconds = COOLDOWN_SECONDS - elapsedSeconds;
			}
		}

		return new CodeStatusResponse(active, cooldownSeconds);
	}

	private AuthCode findMatchingCode(String email, String code, String purpose) {
		List<AuthCode> codes = authCodeRepository
				.findByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
						email, purpose, LocalDateTime.now());

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
}

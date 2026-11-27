package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LinkState;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneAuthResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneStartRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneVerifyRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.UserResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.PhoneVerification;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixCustomerIndex;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixLinkChallenge;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.PhoneVerificationRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixCustomerIndexRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixLinkChallengeRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Ingreso por teléfono. El OTP solo verifica el número: si la cuenta ya está
 * vinculada, emite sesión; si no, deja una verificación pendiente y NO crea la
 * fila de `users`. La cuenta nace al completar la vinculación en Softix.
 */
@Service
@Slf4j
public class AuthService {

	private static final int MAX_CANDIDATES = 3;
	private static final int MAX_FAILED_ATTEMPTS_PER_HOUR = 3;
	private static final int CHALLENGE_EXPIRY_MINUTES = 15;
	private static final int REGISTRATION_EXPIRY_MINUTES = 15;

	private final UserRepository userRepository;
	private final PhoneVerificationRepository verificationRepository;
	private final SoftixCustomerIndexRepository indexRepository;
	private final SoftixLinkChallengeRepository challengeRepository;
	private final OtpService otpService;
	private final JwtService jwtService;
	private final SessionService sessionService;
	private final PhoneNormalizer phoneNormalizer;
	private final CaptchaService captchaService;
	private final String consentVersion;

	public AuthService(UserRepository userRepository,
			PhoneVerificationRepository verificationRepository,
			SoftixCustomerIndexRepository indexRepository,
			SoftixLinkChallengeRepository challengeRepository,
			OtpService otpService,
			JwtService jwtService,
			SessionService sessionService,
			PhoneNormalizer phoneNormalizer,
			CaptchaService captchaService,
			@Value("${legal.consent-version:2026-11}") String consentVersion) {
		this.userRepository = userRepository;
		this.verificationRepository = verificationRepository;
		this.indexRepository = indexRepository;
		this.challengeRepository = challengeRepository;
		this.otpService = otpService;
		this.jwtService = jwtService;
		this.sessionService = sessionService;
		this.phoneNormalizer = phoneNormalizer;
		this.captchaService = captchaService;
		this.consentVersion = consentVersion;
	}

	@Transactional
	public void start(PhoneStartRequest request, String remoteIp, String userAgent,
			String deviceId) {
		String phone = phoneNormalizer.normalize(request.phone());
		captchaService.verify(request.captchaToken(), remoteIp);
		otpService.requestCode(phone, OtpService.PURPOSE_LOGIN, remoteIp, userAgent,
				deviceId);
	}

	@Transactional(noRollbackFor = ApiException.class)
	public VerifyResult verify(PhoneVerifyRequest request, String remoteIp) {
		String phone = phoneNormalizer.normalize(request.phone());
		if (!otpService.verifyAndConsume(phone, OtpService.PURPOSE_LOGIN, request.code())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado");
		}

		User user = userRepository.findByPhoneE164(phone).orElse(null);
		if (user != null && user.getSoftixClientId() != null) {
			user.setPhoneVerifiedAt(LocalDateTime.now());
			user.setLastLoginAt(LocalDateTime.now());
			applyConsent(user, request, remoteIp);
			user = userRepository.save(user);

			SessionService.IssuedSession session = sessionService.issue(user);
			PhoneAuthResponse response = new PhoneAuthResponse(session.accessToken(),
					toUserResponse(user), LinkState.linked(), false);
			return new VerifyResult(response, session.refreshToken(), null);
		}

		if (!Boolean.TRUE.equals(request.consentAccepted())) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Debes aceptar el tratamiento de datos para crear tu cuenta");
		}

		PhoneVerification verification = new PhoneVerification();
		verification.setId(UUID.randomUUID());
		verification.setPhoneE164(phone);
		verification.setUserId(user == null ? null : user.getId());
		verification.setConsentVersion(consentVersion);
		verification.setConsentAt(LocalDateTime.now());
		verification.setConsentIp(remoteIp);
		verification.setCreatedAt(LocalDateTime.now());
		verification.setExpiresAt(LocalDateTime.now().plusMinutes(REGISTRATION_EXPIRY_MINUTES));
		verification.setConsumed(false);
		verification = verificationRepository.save(verification);

		LinkState link = linkStateForVerification(verification);
		String registrationToken = jwtService.generateRegistrationToken(
				verification.getId(), phone);
		PhoneAuthResponse response = new PhoneAuthResponse(null, null, link, true);
		return new VerifyResult(response, null, registrationToken);
	}

	@Transactional
	public SessionResult refresh(String refreshToken) {
		SessionService.RefreshResult result = sessionService.refresh(refreshToken);
		PhoneAuthResponse response = new PhoneAuthResponse(result.accessToken(),
				toUserResponse(result.user()), LinkState.linked(), false);
		return new SessionResult(response, result.refreshToken());
	}

	@Transactional
	public void logout(String refreshToken) {
		sessionService.logout(refreshToken);
	}

	@Transactional(readOnly = true)
	public UserResponse getMe() {
		return toUserResponse(currentUser());
	}

	public User currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null) {
			throw unauthorized();
		}
		UUID userId;
		try {
			userId = UUID.fromString(auth.getPrincipal().toString());
		} catch (IllegalArgumentException ex) {
			throw unauthorized();
		}
		return userRepository.findById(userId)
				.orElseThrow(AuthService::unauthorized);
	}

	public UserResponse toUserResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getPhoneE164(),
				user.getPhoneVerifiedAt() != null,
				user.getSoftixClientId() != null,
				Boolean.TRUE.equals(user.getIsAdmin()));
	}

	private LinkState linkStateForVerification(PhoneVerification verification) {
		List<SoftixCustomerIndex> matches = indexRepository
				.findByPhoneE164OrderBySoftixClientId(verification.getPhoneE164());
		if (matches.isEmpty()) {
			return LinkState.needsCreate();
		}
		if (matches.size() > MAX_CANDIDATES) {
			return LinkState.contactStaff();
		}

		LocalDateTime now = LocalDateTime.now();
		int recentAttempts = challengeRepository
				.findByPhoneE164AndCreatedAtAfter(verification.getPhoneE164(), now.minusHours(1))
				.stream()
				.mapToInt(SoftixLinkChallenge::getAttempts)
				.sum();
		if (recentAttempts >= MAX_FAILED_ATTEMPTS_PER_HOUR) {
			return LinkState.contactStaff();
		}

		SoftixLinkChallenge challenge = challengeRepository
				.findFirstByPhoneVerificationIdAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
						verification.getId(), now)
				.orElseGet(() -> createChallenge(verification, matches, now));

		List<String> hints = new ArrayList<>();
		if (matches.size() > 1) {
			for (SoftixCustomerIndex match : matches) {
				hints.add(DocumentNumbers.mask(match.getSoftixClientId()));
			}
		}
		return LinkState.needsDocument(challenge.getId().toString(), hints);
	}

	private SoftixLinkChallenge createChallenge(PhoneVerification verification,
			List<SoftixCustomerIndex> matches, LocalDateTime now) {
		List<String> candidates = matches.stream()
				.map(SoftixCustomerIndex::getSoftixClientId)
				.toList();
		SoftixLinkChallenge challenge = new SoftixLinkChallenge();
		challenge.setId(UUID.randomUUID());
		challenge.setPhoneVerificationId(verification.getId());
		challenge.setPhoneE164(verification.getPhoneE164());
		challenge.setCandidateClientIds(String.join(",", candidates));
		challenge.setAttempts(0);
		challenge.setExpiresAt(now.plusMinutes(CHALLENGE_EXPIRY_MINUTES));
		challenge.setConsumed(false);
		challenge.setCreatedAt(now);
		return challengeRepository.save(challenge);
	}

	private void applyConsent(User user, PhoneVerifyRequest request, String remoteIp) {
		if (user.getConsentAt() == null && Boolean.TRUE.equals(request.consentAccepted())) {
			user.setConsentVersion(consentVersion);
			user.setConsentAt(LocalDateTime.now());
			user.setConsentIp(remoteIp);
		}
	}

	private static ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "Sesión inválida o expirada");
	}

	public record VerifyResult(PhoneAuthResponse response, String refreshToken,
			String registrationToken) {
	}

	public record SessionResult(PhoneAuthResponse response, String refreshToken) {
	}
}

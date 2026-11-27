package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneAuthResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneChallengeConfirmRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneDocumentLookupRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.RegistrationProfileResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateCustomerRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LinkState;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.PhoneVerification;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixLinkChallenge;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.PhoneVerificationRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixLinkChallengeRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

import lombok.AllArgsConstructor;

/**
 * Completa el registro: valida la verificación pendiente, vincula o crea la
 * ficha en Softix y solo entonces crea la cuenta local y emite la sesión.
 */
@Service
@AllArgsConstructor
public class RegistrationService {

	private static final int MAX_CHALLENGE_ATTEMPTS = 3;
	private static final int MAX_DOCUMENT_LOOKUPS = 5;

	private final PhoneVerificationRepository verificationRepository;
	private final SoftixLinkChallengeRepository challengeRepository;
	private final UserRepository userRepository;
	private final SoftixCustomerGateway gateway;
	private final SessionService sessionService;
	private final JwtService jwtService;
	private final AuthService authService;
	private final SoftixWriteLimiter writeLimiter;

	@Transactional(noRollbackFor = ApiException.class)
	public RegistrationProfileResponse confirmLinkDocument(String registrationToken,
			PhoneChallengeConfirmRequest request) {
		PhoneVerification verification = requireVerification(registrationToken);

		UUID challengeId = parseUuid(request.challengeId());
		SoftixLinkChallenge challenge = challengeRepository
				.findByIdAndConsumedFalseAndExpiresAtAfter(challengeId, LocalDateTime.now())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"El reto expiró. Vuelve a iniciar sesión."));

		if (!challenge.getPhoneVerificationId().equals(verification.getId())
				|| !challenge.getPhoneE164().equals(verification.getPhoneE164())) {
			throw new ApiException(HttpStatus.NOT_FOUND,
					"El reto expiró. Vuelve a iniciar sesión.");
		}

		List<String> candidates = Arrays.stream(challenge.getCandidateClientIds().split(","))
				.filter(value -> !value.isBlank())
				.toList();
		String match = gateway.findMatch(candidates, request.documentId());
		if (match == null) {
			challenge.setAttempts(challenge.getAttempts() + 1);
			if (challenge.getAttempts() >= MAX_CHALLENGE_ATTEMPTS) {
				challenge.setConsumed(true);
			}
			challengeRepository.save(challenge);
			if (Boolean.TRUE.equals(challenge.getConsumed())) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
						"No pudimos verificar tus datos. Contáctanos.");
			}
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"No pudimos verificar tus datos. Revisa el documento e intenta de nuevo.");
		}

		CustomerResponse customer = gateway.fetchCustomer(match);
		if (customer.documentId() == null || !customer.documentId().equals(match)) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No pudimos validar tus datos. Intenta de nuevo.");
		}
		ensureLinkable(customer, verification.getPhoneE164());

		challenge.setConsumed(true);
		challengeRepository.save(challenge);
		return toRegistrationProfile(customer);
	}

	/**
	 * Busca una ficha por documento mientras el formulario de registro está
	 * abierto. Si existe y puede vincularse, devuelve los datos para precargar
	 * el formulario; si no existe, el formulario sigue como creación normal.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public RegistrationProfileResponse lookupDocument(String registrationToken,
			PhoneDocumentLookupRequest request) {
		PhoneVerification verification = requireVerification(registrationToken);
		int lookups = verification.getDocumentLookupAttempts() == null ? 0
				: verification.getDocumentLookupAttempts();
		if (lookups >= MAX_DOCUMENT_LOOKUPS) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Has hecho demasiadas consultas. Intenta de nuevo más tarde.");
		}
		verification.setDocumentLookupAttempts(lookups + 1);
		verificationRepository.save(verification);

		CustomerResponse customer;
		try {
			customer = gateway.fetchCustomer(request.documentId());
		} catch (ApiException ex) {
			if (ex.getStatus() == HttpStatus.NOT_FOUND) {
				return RegistrationProfileResponse.create(request.documentType(),
						request.documentId());
			}
			throw ex;
		}
		ensureLinkable(customer, verification.getPhoneE164());
		return toRegistrationProfile(customer);
	}

	/**
	 * Confirma el formulario de registro. Si la ficha ya existe se actualiza
	 * con los datos editados y se vincula; si no existe, se crea. La cuenta
	 * local se crea en ambos casos solo en este punto.
	 */
	@Transactional
	public CompletedRegistration completeLinkProfile(String registrationToken,
			SoftixCreateCustomerRequest request) {
		PhoneVerification verification = requireVerification(registrationToken);
		String phone = verification.getPhoneE164();

		writeLimiter.checkAndRecord(phone, SoftixWriteLimiter.OPERATION_LINK);

		CustomerResponse current = gateway.fetchCustomer(request.documentId());
		if (current.documentId() == null || !current.documentId().equals(request.documentId())) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No pudimos validar tus datos. Intenta de nuevo.");
		}
		ensureLinkable(current, phone);

		CustomerCreateRequest body = gateway.mergeForLink(current, request, phone);
		gateway.updateCustomer(current.documentId(), body);

		User user = createOrLinkUser(verification, current.documentId());
		gateway.upsertIndex(phone, current.documentId());

		SessionService.IssuedSession session = sessionService.issue(user);
		PhoneAuthResponse response = new PhoneAuthResponse(session.accessToken(),
				authService.toUserResponse(user), LinkState.linked(), false);
		return new CompletedRegistration(response, session.refreshToken());
	}

	@Transactional
	public CompletedRegistration completeCreate(String registrationToken,
			SoftixCreateCustomerRequest request) {
		PhoneVerification verification = requireVerification(registrationToken);
		String phone = verification.getPhoneE164();

		writeLimiter.checkAndRecord(phone, SoftixWriteLimiter.OPERATION_CREATE);

		if (gateway.hasIndexForPhone(phone)) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Tu número ya tiene datos registrados. Verifica tu documento para continuar.");
		}
		if (!gateway.isDocumentAvailable(request.documentId())) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Este documento ya está registrado con otro número de teléfono. Contáctanos.");
		}

		CustomerCreateRequest body = gateway.buildCreateRequest(request, phone);
		gateway.createCustomer(body);

		User user = createOrLinkUser(verification, request.documentId());
		gateway.upsertIndex(phone, request.documentId());

		SessionService.IssuedSession session = sessionService.issue(user);
		PhoneAuthResponse response = new PhoneAuthResponse(session.accessToken(),
				authService.toUserResponse(user), LinkState.linked(), false);
		return new CompletedRegistration(response, session.refreshToken());
	}

	private void ensureLinkable(CustomerResponse customer, String phoneE164) {
		if (customer.documentId() == null || customer.documentId().isBlank()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No pudimos validar tus datos. Intenta de nuevo.");
		}
		if (userRepository.existsBySoftixClientId(customer.documentId())) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Este documento ya está vinculado a otra cuenta. Contáctanos.");
		}
		if (!gateway.isSupportedDocumentType(customer.documentType())) {
			throw new ApiException(HttpStatus.CONFLICT,
					"No pudimos vincular este documento. Contáctanos.");
		}
		if (!gateway.phoneMatches(customer, phoneE164)) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Este documento ya está registrado con otro número de teléfono. Contáctanos.");
		}
	}

	private RegistrationProfileResponse toRegistrationProfile(CustomerResponse customer) {
		return RegistrationProfileResponse.link(
				customer.documentType(),
				customer.documentId(),
				customer.firstName(),
				customer.middleName(),
				customer.lastName(),
				customer.secondLastName(),
				customer.email(),
				customer.birthDate() == null ? null : customer.birthDate().toLocalDate(),
				customer.gender());
	}

	private PhoneVerification requireVerification(String registrationToken) {
		if (registrationToken == null || !jwtService.isRegistrationToken(registrationToken)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED,
					"La verificación expiró. Vuelve a iniciar sesión.");
		}
		UUID verificationId;
		String phone;
		try {
			verificationId = jwtService.extractRegistrationId(registrationToken);
			phone = jwtService.extractPhone(registrationToken);
		} catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED,
					"La verificación expiró. Vuelve a iniciar sesión.");
		}
		PhoneVerification verification = verificationRepository
				.findByIdAndConsumedFalseAndExpiresAtAfter(verificationId, LocalDateTime.now())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
						"La verificación expiró. Vuelve a iniciar sesión."));
		if (phone == null || !verification.getPhoneE164().equals(phone)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED,
					"La verificación expiró. Vuelve a iniciar sesión.");
		}
		return verification;
	}

	private User createOrLinkUser(PhoneVerification verification, String documentId) {
		User user;
		if (verification.getUserId() != null) {
			user = userRepository.findById(verification.getUserId())
					.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
							"La verificación expiró. Vuelve a iniciar sesión."));
			if (user.getSoftixClientId() != null) {
				throw new ApiException(HttpStatus.CONFLICT,
						"Tu cuenta ya está vinculada a una ficha");
			}
		} else {
			user = new User();
			user.setPhoneE164(verification.getPhoneE164());
			user.setIsAdmin(false);
		}

		user.setPhoneVerifiedAt(verification.getCreatedAt());
		user.setSoftixClientId(documentId);
		user.setSoftixLinkedAt(LocalDateTime.now());
		user.setConsentVersion(verification.getConsentVersion());
		user.setConsentAt(verification.getConsentAt());
		user.setConsentIp(verification.getConsentIp());
		user.setLastLoginAt(LocalDateTime.now());
		user = userRepository.save(user);

		verification.setConsumed(true);
		verificationRepository.save(verification);
		return user;
	}

	private UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Reto inválido");
		}
	}

	public record CompletedRegistration(PhoneAuthResponse response, String refreshToken) {
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.DocumentTypeResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneAuthResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneChallengeConfirmRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneDocumentLookupRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneStartRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PhoneVerifyRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.RegistrationProfileResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateCustomerRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AuthService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.CookieService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.RegistrationService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixProfileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RegistrationService registrationService;
	private final SoftixProfileService softixProfileService;
	private final CookieService cookieService;

	@PostMapping("/phone/start")
	public ResponseEntity<Void> start(@Valid @RequestBody PhoneStartRequest request,
			HttpServletRequest servletRequest) {
		authService.start(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/phone/verify")
	public ResponseEntity<PhoneAuthResponse> verify(@Valid @RequestBody PhoneVerifyRequest request,
			HttpServletRequest servletRequest, HttpServletResponse response) {
		AuthService.VerifyResult result = authService.verify(request, clientIp(servletRequest));
		if (result.registrationToken() != null) {
			cookieService.setRegistrationCookie(response, result.registrationToken());
		} else {
			cookieService.setRefreshCookie(response, result.refreshToken());
		}
		return ResponseEntity.ok(result.response());
	}

	@GetMapping("/phone/document-types")
	public ResponseEntity<List<DocumentTypeResponse>> documentTypes() {
		return ResponseEntity.ok(softixProfileService.documentTypes());
	}

	@PostMapping("/phone/confirm")
	public ResponseEntity<RegistrationProfileResponse> confirm(
			@CookieValue(name = "registration", required = false) String registrationToken,
			@Valid @RequestBody PhoneChallengeConfirmRequest request) {
		return ResponseEntity.ok(registrationService.confirmLinkDocument(registrationToken, request));
	}

	@PostMapping("/phone/lookup")
	public ResponseEntity<RegistrationProfileResponse> lookup(
			@CookieValue(name = "registration", required = false) String registrationToken,
			@Valid @RequestBody PhoneDocumentLookupRequest request) {
		return ResponseEntity.ok(registrationService.lookupDocument(registrationToken, request));
	}

	@PostMapping("/phone/link")
	public ResponseEntity<PhoneAuthResponse> link(
			@CookieValue(name = "registration", required = false) String registrationToken,
			@Valid @RequestBody SoftixCreateCustomerRequest request,
			HttpServletResponse response) {
		RegistrationService.CompletedRegistration completed = registrationService
				.completeLinkProfile(registrationToken, request);
		cookieService.clearRegistrationCookie(response);
		cookieService.setRefreshCookie(response, completed.refreshToken());
		return ResponseEntity.ok(completed.response());
	}

	@PostMapping("/phone/create")
	public ResponseEntity<PhoneAuthResponse> create(
			@CookieValue(name = "registration", required = false) String registrationToken,
			@Valid @RequestBody SoftixCreateCustomerRequest request,
			HttpServletResponse response) {
		RegistrationService.CompletedRegistration completed = registrationService
				.completeCreate(registrationToken, request);
		cookieService.clearRegistrationCookie(response);
		cookieService.setRefreshCookie(response, completed.refreshToken());
		return ResponseEntity.ok(completed.response());
	}

	@PostMapping("/refresh")
	public ResponseEntity<PhoneAuthResponse> refresh(
			@CookieValue(name = "refresh", required = false) String refreshToken,
			HttpServletResponse response) {
		if (refreshToken == null) {
			return ResponseEntity.status(401).build();
		}
		AuthService.SessionResult session = authService.refresh(refreshToken);
		cookieService.setRefreshCookie(response, session.refreshToken());
		return ResponseEntity.ok(session.response());
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = "refresh", required = false) String refreshToken,
			HttpServletResponse response) {
		authService.logout(refreshToken);
		cookieService.clearRefreshCookie(response);
		return ResponseEntity.noContent().build();
	}

	private String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}

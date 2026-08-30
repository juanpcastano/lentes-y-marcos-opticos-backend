package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AuthResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AuthResult;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CodeStatusResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.GoogleLoginRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LoginCodeRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LoginCodeVerifyRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LoginOptionsRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LoginOptionsResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.LoginRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PasswordCodeVerifyRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PasswordResetConfirmRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PasswordResetRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PasswordResetVerifyRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PasswordSetRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProfileUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.RefreshResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SignupRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.UserResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AuthService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.CookieService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * AuthController
 */
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final CookieService cookieService;

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request,
			HttpServletResponse response) {
		AuthResult result = authService.register(request);
		cookieService.setRefreshCookie(response, result.refreshToken());
		return ResponseEntity.created(URI.create("/api/users/me"))
				.body(new AuthResponse(result.accessToken(), result.user()));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletResponse response) {
		AuthResult result = authService.login(request);
		cookieService.setRefreshCookie(response, result.refreshToken());
		return ResponseEntity.ok(new AuthResponse(result.accessToken(), result.user()));
	}

	@PostMapping("/login/options")
	public ResponseEntity<LoginOptionsResponse> loginOptions(@Valid @RequestBody LoginOptionsRequest request) {
		return ResponseEntity.ok(authService.getLoginOptions(request));
	}

	@PostMapping("/login/code-request")
	public ResponseEntity<Void> requestLoginCode(@Valid @RequestBody LoginCodeRequest request) {
		authService.requestLoginCode(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login/code-verify")
	public ResponseEntity<AuthResponse> verifyLoginCode(@Valid @RequestBody LoginCodeVerifyRequest request,
			HttpServletResponse response) {
		AuthResult result = authService.verifyLoginCode(request);
		cookieService.setRefreshCookie(response, result.refreshToken());
		return ResponseEntity.ok(new AuthResponse(result.accessToken(), result.user()));
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
			HttpServletResponse response) {
		AuthResult result = authService.loginWithGoogle(request);
		cookieService.setRefreshCookie(response, result.refreshToken());
		return ResponseEntity.ok(new AuthResponse(result.accessToken(), result.user()));
	}

	@PostMapping("/link/google")
	public ResponseEntity<UserResponse> linkGoogle(@Valid @RequestBody GoogleLoginRequest request) {
		return ResponseEntity.ok(authService.linkGoogle(request));
	}

	@DeleteMapping("/link/google")
	public ResponseEntity<UserResponse> unlinkGoogle() {
		return ResponseEntity.ok(authService.unlinkGoogle());
	}

	@PostMapping("/refresh")
	public ResponseEntity<RefreshResponse> refresh(
			@CookieValue(name = "refresh", required = false) String refreshToken,
			HttpServletResponse response) {
		if (refreshToken == null) {
			return ResponseEntity.status(401).build();
		}
		AuthResult result = authService.refresh(refreshToken);
		cookieService.setRefreshCookie(response, result.refreshToken());
		return ResponseEntity.ok(new RefreshResponse(result.accessToken(), result.user()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletResponse response) {
		cookieService.clearRefreshCookie(response);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password/code-request")
	public ResponseEntity<Void> requestPasswordCode() {
		authService.requestPasswordCode();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/password/code-status")
	public ResponseEntity<CodeStatusResponse> passwordCodeStatus() {
		return ResponseEntity.ok(authService.getPasswordCodeStatus());
	}

	@PostMapping("/password/code-verify")
	public ResponseEntity<Void> verifyPasswordCode(@Valid @RequestBody PasswordCodeVerifyRequest request) {
		authService.verifyPasswordCode(request);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/password/set")
	public ResponseEntity<Void> setPassword(@Valid @RequestBody PasswordSetRequest request) {
		authService.setPassword(request);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/profile")
	public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
		return ResponseEntity.ok(authService.updateProfile(request));
	}

	@PostMapping("/password/reset/request")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		authService.requestPasswordReset(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password/reset/verify")
	public ResponseEntity<Void> verifyPasswordReset(@Valid @RequestBody PasswordResetVerifyRequest request) {
		authService.verifyPasswordReset(request);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/password/reset/confirm")
	public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		authService.confirmPasswordReset(request);
		return ResponseEntity.noContent().build();
	}

}

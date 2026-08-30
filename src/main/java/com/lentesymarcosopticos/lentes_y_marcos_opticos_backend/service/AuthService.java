package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SignupRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.UserResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

import lombok.AllArgsConstructor;

/**
 * AuthService
 */
@Service
@AllArgsConstructor
public class AuthService {

	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final OtpService otpService;
	private final GoogleService googleService;

	public AuthResult register(SignupRequest signupRequest) {
		if (userRepository.existsByEmail(signupRequest.email())) {
			throw new ApiException(HttpStatus.CONFLICT, "Este email ya está registrado");
		}
		User userEntity = new User();
		userEntity.setName(signupRequest.name());
		userEntity.setEmail(signupRequest.email());
		userEntity.setPhone(signupRequest.phone());

		String passwordHash = passwordEncoder.encode(signupRequest.password());
		userEntity.setPasswordHash(passwordHash);

		userEntity.setIsAdmin(false);
		User savedUser = userRepository.save(userEntity);
		UserResponse user = toUserResponse(savedUser);
		return new AuthResult(
				jwtService.generateAccessToken(signupRequest.email()),
				jwtService.generateRefreshToken(signupRequest.email()),
				user);
	}

	public AuthResult login(LoginRequest loginRequest) {

		Optional<User> userQuery = userRepository.findByEmail(loginRequest.email());
		User userEntity = userQuery
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

		if (!passwordEncoder.matches(loginRequest.password(), userEntity.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
		}

		UserResponse user = toUserResponse(userEntity);
		return new AuthResult(
				jwtService.generateAccessToken(loginRequest.email()),
				jwtService.generateRefreshToken(loginRequest.email()),
				user);
	}

	public LoginOptionsResponse getLoginOptions(LoginOptionsRequest request) {
		return userRepository.findByEmail(request.email())
				.map(user -> new LoginOptionsResponse(
						user.getPasswordHash() != null,
						user.getGoogleSub() != null))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"Este email no está registrado"));
	}

	public void requestLoginCode(LoginCodeRequest request) {
		Optional<User> userQuery = userRepository.findByEmail(request.email());
		if (userQuery.isEmpty()) {
			return;
		}
		otpService.generateAndSend(request.email(), "login");
	}

	public AuthResult verifyLoginCode(LoginCodeVerifyRequest request) {
		if (!otpService.verifyAndConsume(request.email(), request.code(), "login")) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado");
		}

		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado"));

		return new AuthResult(
				jwtService.generateAccessToken(request.email()),
				jwtService.generateRefreshToken(request.email()),
				toUserResponse(user));
	}

	public AuthResult refresh(String refreshToken) {
		if (!jwtService.isRefreshToken(refreshToken)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado");
		}

		String email = jwtService.extractEmail(refreshToken);
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado"));

		return new AuthResult(
				jwtService.generateAccessToken(email),
				jwtService.generateRefreshToken(email),
				toUserResponse(user));
	}

	public UserResponse getMe() {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String email = (String) auth.getPrincipal();

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

		return toUserResponse(user);
	}

	public void requestPasswordCode() {
		String email = getCurrentUserEmail();
		otpService.generateAndSend(email, "manage_password");
	}

	public CodeStatusResponse getPasswordCodeStatus() {
		String email = getCurrentUserEmail();
		return otpService.getCodeStatus(email, "manage_password");
	}

	public void verifyPasswordCode(PasswordCodeVerifyRequest request) {
		String email = getCurrentUserEmail();
		if (!otpService.verifyOnly(email, request.code(), "manage_password")) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado");
		}
	}

	public void setPassword(PasswordSetRequest request) {
		String email = getCurrentUserEmail();
		if (!otpService.verifyAndConsume(email, request.code(), "manage_password")) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado");
		}

		User user = getCurrentUser();
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}

	public UserResponse updateProfile(ProfileUpdateRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String email = (String) auth.getPrincipal();

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

		user.setName(request.name());
		if (request.phone() != null) {
			user.setPhone(request.phone());
		}
		userRepository.save(user);

		return toUserResponse(user);
	}

	public void requestPasswordReset(PasswordResetRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"Este email no está registrado"));
		otpService.generateAndSend(user.getEmail(), "reset_password");
	}

	public void verifyPasswordReset(PasswordResetVerifyRequest request) {
		if (!otpService.verifyOnly(request.email(), request.code(), "reset_password")) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado");
		}
	}

	public void confirmPasswordReset(PasswordResetConfirmRequest request) {
		if (!otpService.verifyAndConsume(request.email(), request.code(), "reset_password")) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado");
		}

		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Código inválido o expirado"));

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}

	public AuthResult loginWithGoogle(GoogleLoginRequest request) {
		var payload = googleService.verify(request.idToken());
		String googleSub = payload.getSubject();
		String email = payload.getEmail();
		String name = (String) payload.get("name");
		if (name == null || name.isBlank()) {
			name = email;
		}

		User user = userRepository.findByGoogleSub(googleSub).orElse(null);

		if (user == null) {
			Optional<User> byEmail = userRepository.findByEmail(email);
			if (byEmail.isPresent()) {
				user = byEmail.get();
				user.setGoogleSub(googleSub);
				user = userRepository.save(user);
			} else {
				user = new User();
				user.setName(name);
				user.setEmail(email);
				user.setGoogleSub(googleSub);
				user.setIsAdmin(false);
				user = userRepository.save(user);
			}
		}

		return new AuthResult(
				jwtService.generateAccessToken(user.getEmail()),
				jwtService.generateRefreshToken(user.getEmail()),
				toUserResponse(user));
	}

	public UserResponse linkGoogle(GoogleLoginRequest request) {
		var payload = googleService.verify(request.idToken());
		String googleSub = payload.getSubject();
		String googleEmail = payload.getEmail();

		User user = getCurrentUser();

		Optional<User> alreadyLinked = userRepository.findByGoogleSub(googleSub);
		if (alreadyLinked.isPresent() && !alreadyLinked.get().getId().equals(user.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "Esta cuenta de Google ya está vinculada a otro usuario");
		}

		if (!googleEmail.equals(user.getEmail())) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El email de la cuenta de Google no coincide con el email de tu cuenta");
		}

		user.setGoogleSub(googleSub);
		userRepository.save(user);
		return toUserResponse(user);
	}

	public UserResponse unlinkGoogle() {
		User user = getCurrentUser();

		if (user.getPasswordHash() == null) {
			throw new ApiException(HttpStatus.CONFLICT,
					"No puedes desvincular Google porque es tu único método de acceso");
		}

		user.setGoogleSub(null);
		userRepository.save(user);
		return toUserResponse(user);
	}

	private User getCurrentUser() {
		String email = getCurrentUserEmail();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
	}

	private String getCurrentUserEmail() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return (String) auth.getPrincipal();
	}

	private UserResponse toUserResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getPhone(),
				user.getPasswordHash() != null,
				user.getGoogleSub() != null,
				user.getIsAdmin());
	}
}

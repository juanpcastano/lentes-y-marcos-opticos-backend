package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthSession;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.AuthSessionRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Emisión, rotación y revocación de sesiones de refresh. Solo se emiten para
 * usuarios ya vinculados a Softix.
 */
@Service
@AllArgsConstructor
@Slf4j
public class SessionService {

	private final AuthSessionRepository sessionRepository;
	private final UserRepository userRepository;
	private final JwtService jwtService;

	public IssuedSession issue(User user) {
		UUID sessionId = UUID.randomUUID();
		AuthSession session = new AuthSession();
		session.setId(sessionId);
		session.setUserId(user.getId());
		session.setCreatedAt(LocalDateTime.now());
		session.setExpiresAt(LocalDateTime.now()
				.plus(Duration.ofMillis(jwtService.getRefreshExpirationMs())));
		sessionRepository.save(session);

		String accessToken = jwtService.generateAccessToken(user.getId(),
				Boolean.TRUE.equals(user.getIsAdmin()));
		String refreshToken = jwtService.generateRefreshToken(user.getId(), sessionId);
		return new IssuedSession(accessToken, refreshToken);
	}

	@Transactional
	public RefreshResult refresh(String refreshToken) {
		if (refreshToken == null || !jwtService.isRefreshToken(refreshToken)) {
			throw unauthorized();
		}

		UUID userId;
		UUID sessionId;
		try {
			userId = jwtService.extractUserId(refreshToken);
			sessionId = jwtService.extractSessionId(refreshToken);
		} catch (RuntimeException ex) {
			throw unauthorized();
		}
		if (userId == null || sessionId == null) {
			throw unauthorized();
		}

		AuthSession session = sessionRepository
				.findByIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, LocalDateTime.now())
				.orElseThrow(SessionService::unauthorized);
		if (!session.getUserId().equals(userId)) {
			throw unauthorized();
		}

		User user = userRepository.findById(userId).orElseThrow(SessionService::unauthorized);
		session.setRevokedAt(LocalDateTime.now());
		sessionRepository.save(session);

		IssuedSession issued = issue(user);
		return new RefreshResult(user, issued.accessToken(), issued.refreshToken());
	}

	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || !jwtService.isRefreshToken(refreshToken)) {
			return;
		}
		try {
			UUID sessionId = jwtService.extractSessionId(refreshToken);
			if (sessionId == null) {
				return;
			}
			sessionRepository.findById(sessionId).ifPresent(session -> {
				if (session.getRevokedAt() == null) {
					session.setRevokedAt(LocalDateTime.now());
					sessionRepository.save(session);
				}
			});
		} catch (RuntimeException ex) {
			log.debug("Refresh token inválido al cerrar sesión");
		}
	}

	private static ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "Sesión inválida o expirada");
	}

	public record IssuedSession(String accessToken, String refreshToken) {
	}

	public record RefreshResult(User user, String accessToken, String refreshToken) {
	}
}

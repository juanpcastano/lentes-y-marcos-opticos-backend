package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Access token con `sub` = UUID del usuario. Refresh token con `sub` = UUID y
 * `jti` = UUID de la sesión, para poder revocarlo. El token de registro tiene
 * `sub` = UUID de la verificación pendiente y `phone`.
 */
@Service
public class JwtService {

	private static final String ACCESS_TYPE = "access";
	private static final String REFRESH_TYPE = "refresh";
	private static final String REGISTRATION_TYPE = "registration";

	private final SecretKey key;
	private final long accessExpiration;
	private final long refreshExpiration;
	private final long registrationExpiration;
	private final JwtParser parser;

	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-expiration}") long accessExpiration,
			@Value("${jwt.refresh-expiration}") long refreshExpiration,
			@Value("${jwt.registration-expiration:900000}") long registrationExpiration) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessExpiration = accessExpiration;
		this.refreshExpiration = refreshExpiration;
		this.registrationExpiration = registrationExpiration;
		this.parser = Jwts.parser().verifyWith(key).build();
	}

	public String generateAccessToken(UUID userId, boolean admin) {
		return buildToken(userId.toString(), ACCESS_TYPE, accessExpiration, admin, null, null);
	}

	public String generateRefreshToken(UUID userId, UUID sessionId) {
		return buildToken(userId.toString(), REFRESH_TYPE, refreshExpiration, false, sessionId, null);
	}

	public String generateRegistrationToken(UUID verificationId, String phoneE164) {
		return buildToken(verificationId.toString(), REGISTRATION_TYPE, registrationExpiration,
				false, null, phoneE164);
	}

	private String buildToken(String subject, String type, long expiration, boolean admin,
			UUID sessionId, String phone) {
		var builder = Jwts.builder()
				.subject(subject)
				.claim("type", type)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + expiration))
				.signWith(key, Jwts.SIG.HS512);
		if (admin) {
			builder.claim("admin", true);
		}
		if (sessionId != null) {
			builder.id(sessionId.toString());
		}
		if (phone != null) {
			builder.claim("phone", phone);
		}
		return builder.compact();
	}

	public UUID extractUserId(String token) {
		return UUID.fromString(parser.parseSignedClaims(token).getPayload().getSubject());
	}

	public UUID extractSessionId(String token) {
		String id = parser.parseSignedClaims(token).getPayload().getId();
		return id == null ? null : UUID.fromString(id);
	}

	public UUID extractRegistrationId(String token) {
		return UUID.fromString(parser.parseSignedClaims(token).getPayload().getSubject());
	}

	public String extractPhone(String token) {
		return parser.parseSignedClaims(token).getPayload().get("phone", String.class);
	}

	public String extractType(String token) {
		return parser.parseSignedClaims(token).getPayload().get("type", String.class);
	}

	public boolean isAdminToken(String token) {
		try {
			return Boolean.TRUE.equals(parser.parseSignedClaims(token)
					.getPayload().get("admin", Boolean.class));
		} catch (JwtException e) {
			return false;
		}
	}

	public boolean isAccessToken(String token) {
		try {
			return ACCESS_TYPE.equals(extractType(token));
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public boolean isRefreshToken(String token) {
		try {
			return REFRESH_TYPE.equals(extractType(token));
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public boolean isRegistrationToken(String token) {
		try {
			return REGISTRATION_TYPE.equals(extractType(token));
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public boolean isTokenValid(String token, UUID userId) {
		try {
			return Objects.equals(userId, extractUserId(token));
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public long getRefreshExpirationMs() {
		return refreshExpiration;
	}

	public long getRegistrationExpirationMs() {
		return registrationExpiration;
	}
}

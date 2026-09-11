package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JwtService
 */
@Service
public class JwtService {

	private static final String ACCESS_TYPE = "access";
	private static final String REFRESH_TYPE = "refresh";

	private final SecretKey key;
	private final long accessExpiration;
	private final long refreshExpiration;
	private final JwtParser parser;

	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-expiration}") long accessExpiration,
			@Value("${jwt.refresh-expiration}") long refreshExpiration) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessExpiration = accessExpiration;
		this.refreshExpiration = refreshExpiration;
		this.parser = Jwts.parser().verifyWith(key).build();
	}

	public String generateAccessToken(String email, boolean admin) {
		return buildToken(email, ACCESS_TYPE, accessExpiration, admin);
	}

	public String generateRefreshToken(String email) {
		return buildToken(email, REFRESH_TYPE, refreshExpiration, false);
	}

	private String buildToken(String email, String type, long expiration, boolean admin) {
		var builder = Jwts.builder()
				.subject(email)
				.claim("type", type)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + expiration))
				.signWith(key, Jwts.SIG.HS512);
		if (admin) {
			builder.claim("admin", true);
		}
		return builder.compact();
	}

	public String extractEmail(String token) {
		return parser.parseSignedClaims(token)
				.getPayload()
				.getSubject();
	}

	public String extractType(String token) {
		return parser.parseSignedClaims(token)
				.getPayload()
				.get("type", String.class);
	}

	public boolean isAdminToken(String token) {
		try {
			return Boolean.TRUE.equals(parser.parseSignedClaims(token)
					.getPayload()
					.get("admin", Boolean.class));
		} catch (JwtException e) {
			return false;
		}
	}

	public boolean isAccessToken(String token) {
		try {
			return ACCESS_TYPE.equals(extractType(token));
		} catch (JwtException e) {
			return false;
		}
	}

	public boolean isRefreshToken(String token) {
		try {
			return REFRESH_TYPE.equals(extractType(token));
		} catch (JwtException e) {
			return false;
		}
	}

	public Boolean isTokenValid(String token, String email) {
		try {
			return Objects.equals(email, extractEmail(token));
		} catch (JwtException e) {
			return false;
		}
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class CookieService {

	private static final String REFRESH_COOKIE_NAME = "refresh";
	private static final String REGISTRATION_COOKIE_NAME = "registration";

	private final boolean secure;
	private final String sameSite;
	private final String domain;
	private final String refreshPath;
	private final long refreshMaxAgeSeconds;
	private final long registrationMaxAgeSeconds;

	public CookieService(
			@Value("${cookie.secure}") boolean secure,
			@Value("${cookie.same-site}") String sameSite,
			@Value("${cookie.domain}") String domain,
			@Value("${cookie.path}") String refreshPath,
			@Value("${jwt.refresh-expiration}") long refreshExpirationMs,
			@Value("${jwt.registration-expiration:900000}") long registrationExpirationMs) {
		this.secure = secure;
		this.sameSite = sameSite;
		this.domain = domain;
		this.refreshPath = refreshPath;
		this.refreshMaxAgeSeconds = refreshExpirationMs / 1000;
		this.registrationMaxAgeSeconds = registrationExpirationMs / 1000;
	}

	public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader("Set-Cookie",
				buildCookie(REFRESH_COOKIE_NAME, refreshPath, refreshToken,
						refreshMaxAgeSeconds).toString());
	}

	public void clearRefreshCookie(HttpServletResponse response) {
		response.addHeader("Set-Cookie",
				buildCookie(REFRESH_COOKIE_NAME, refreshPath, "", 0).toString());
	}

	public void setRegistrationCookie(HttpServletResponse response, String token) {
		response.addHeader("Set-Cookie",
				buildCookie(REGISTRATION_COOKIE_NAME, "/api/auth/phone", token,
						registrationMaxAgeSeconds).toString());
	}

	public void clearRegistrationCookie(HttpServletResponse response) {
		response.addHeader("Set-Cookie",
				buildCookie(REGISTRATION_COOKIE_NAME, "/api/auth/phone", "", 0).toString());
	}

	private ResponseCookie buildCookie(String name, String path, String value, long maxAgeSeconds) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite(sameSite)
				.path(path)
				.maxAge(maxAgeSeconds);

		if (!domain.isEmpty()) {
			builder.domain(domain);
		}

		return builder.build();
	}
}

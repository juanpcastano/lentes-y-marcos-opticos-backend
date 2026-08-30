package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class CookieService {

	private static final String REFRESH_COOKIE_NAME = "refresh";

	private final boolean secure;
	private final String sameSite;
	private final String domain;
	private final String path;
	private final long refreshMaxAgeSeconds;

	public CookieService(
			@Value("${cookie.secure}") boolean secure,
			@Value("${cookie.same-site}") String sameSite,
			@Value("${cookie.domain}") String domain,
			@Value("${cookie.path}") String path,
			@Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
		this.secure = secure;
		this.sameSite = sameSite;
		this.domain = domain;
		this.path = path;
		this.refreshMaxAgeSeconds = refreshExpirationMs / 1000;
	}

	public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, refreshMaxAgeSeconds).toString());
	}

	public void clearRefreshCookie(HttpServletResponse response) {
		response.addHeader("Set-Cookie", buildRefreshCookie("", 0).toString());
	}

	private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
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

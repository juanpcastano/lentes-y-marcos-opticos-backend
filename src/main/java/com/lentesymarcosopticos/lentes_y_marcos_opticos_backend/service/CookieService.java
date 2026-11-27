package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class CookieService {

	private static final String REFRESH_COOKIE_NAME = "refresh";
	private static final String REGISTRATION_COOKIE_NAME = "registration";
	private static final String DEVICE_COOKIE_NAME = "lm_device";
	private static final String DEVICE_COOKIE_PATH = "/api/auth/phone";

	private final boolean secure;
	private final String sameSite;
	private final String domain;
	private final String refreshPath;
	private final long refreshMaxAgeSeconds;
	private final long registrationMaxAgeSeconds;
	private final long deviceMaxAgeSeconds;

	public CookieService(
			@Value("${cookie.secure}") boolean secure,
			@Value("${cookie.same-site}") String sameSite,
			@Value("${cookie.domain}") String domain,
			@Value("${cookie.path}") String refreshPath,
			@Value("${jwt.refresh-expiration}") long refreshExpirationMs,
			@Value("${jwt.registration-expiration:900000}") long registrationExpirationMs,
			@Value("${cookie.device-max-age-seconds:15552000}") long deviceMaxAgeSeconds) {
		this.secure = secure;
		this.sameSite = sameSite;
		this.domain = domain;
		this.refreshPath = refreshPath;
		this.refreshMaxAgeSeconds = refreshExpirationMs / 1000;
		this.registrationMaxAgeSeconds = registrationExpirationMs / 1000;
		this.deviceMaxAgeSeconds = deviceMaxAgeSeconds;
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

	public String resolveDeviceId(HttpServletRequest request) {
		String existing = readCookie(request, DEVICE_COOKIE_NAME);
		if (isUuid(existing)) {
			return existing;
		}
		return UUID.randomUUID().toString();
	}

	public void setDeviceCookie(HttpServletResponse response, String deviceId) {
		response.addHeader("Set-Cookie",
				buildCookie(DEVICE_COOKIE_NAME, DEVICE_COOKIE_PATH, deviceId,
						deviceMaxAgeSeconds).toString());
	}

	private String readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private boolean isUuid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			UUID.fromString(value);
			return true;
		} catch (IllegalArgumentException ex) {
			return false;
		}
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

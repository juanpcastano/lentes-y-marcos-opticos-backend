package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final Map<String, String> details;

	public ApiException(HttpStatus status, String message) {
		this(status, message, null);
	}

	public ApiException(HttpStatus status, String message, Map<String, String> details) {
		super(message);
		this.status = status;
		this.details = details;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public Map<String, String> getDetails() {
		return details;
	}
}

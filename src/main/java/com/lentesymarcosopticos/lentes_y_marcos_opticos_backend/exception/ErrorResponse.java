package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception;

import java.util.Map;

/**
 * ErrorResponse
 *
 * Unified format for the Errors
 * 
 */
public record ErrorResponse(
		int status,
		String error,
		String message,
		String path,
		Map<String, String> details) {
}

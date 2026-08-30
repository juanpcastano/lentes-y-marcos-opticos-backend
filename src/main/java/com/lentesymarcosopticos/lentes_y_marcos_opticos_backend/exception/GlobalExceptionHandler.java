package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * GlobalExceptionHandler
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
		Map<String, String> details = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.collect(Collectors.toMap(
						FieldError::getField,
						FieldError::getDefaultMessage,
						(existing, replacement) -> existing + "; " + replacement,
						LinkedHashMap::new));

		ErrorResponse response = new ErrorResponse(
				HttpStatus.BAD_REQUEST.value(),
				HttpStatus.BAD_REQUEST.getReasonPhrase(),
				"Validation failed",
				currentPath(),
				details);

		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
		ErrorResponse response = new ErrorResponse(
				ex.getStatus().value(),
				ex.getStatus().getReasonPhrase(),
				ex.getMessage(),
				currentPath(),
				null);

		return ResponseEntity.status(ex.getStatus()).body(response);
	}

	private String currentPath() {
		return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
	}
}

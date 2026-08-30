package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * CustomErrorController
 *
 */
@RestController
public class CustomErrorController implements ErrorController {

	@RequestMapping("/error")
	public ResponseEntity<ErrorResponse> getError(HttpServletRequest request) {
		Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		String message = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
		String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

		HttpStatus status = statusCode != null
				? HttpStatus.resolve(statusCode) != null ? HttpStatus.valueOf(statusCode)
						: HttpStatus.INTERNAL_SERVER_ERROR
				: HttpStatus.INTERNAL_SERVER_ERROR;

		ErrorResponse response = new ErrorResponse(
				status.value(),
				status.getReasonPhrase(),
				message != null ? message : "No additional message available",
				path,
				null);

		return ResponseEntity.status(status).body(response);
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.UserResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AuthService;

import lombok.AllArgsConstructor;

/**
 * UserController
 */
@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
public class UserController {

	private final AuthService authService;

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me() {
		return ResponseEntity.ok(authService.getMe());
	}

}

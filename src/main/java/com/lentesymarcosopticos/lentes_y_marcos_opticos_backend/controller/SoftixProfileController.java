package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixProfileService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/users/me/softix")
@AllArgsConstructor
@Validated
public class SoftixProfileController {

	private final SoftixProfileService softixProfileService;

	@GetMapping("/profile")
	public ResponseEntity<SoftixProfileResponse> profile() {
		return ResponseEntity.ok(softixProfileService.getProfile());
	}

	@PutMapping("/profile")
	public ResponseEntity<SoftixProfileResponse> update(
			@Valid @RequestBody SoftixProfileUpdateRequest request) {
		return ResponseEntity.ok(softixProfileService.updateProfile(request));
	}
}

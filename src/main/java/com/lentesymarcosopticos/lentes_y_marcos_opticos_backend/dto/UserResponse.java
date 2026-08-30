package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.UUID;

/**
 * UserResponse
 */
public record UserResponse(
		UUID id,
		String name,
		String email,
		String phone,
		Boolean hasPassword,
		Boolean hasGoogle,
		Boolean isAdmin) {
}

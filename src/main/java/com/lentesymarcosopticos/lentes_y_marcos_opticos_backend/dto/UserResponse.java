package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String phone,
		boolean phoneVerified,
		boolean softixLinked,
		boolean isAdmin) {
}

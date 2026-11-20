package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * BulkProductStatusRequest — cambia el estado de varios productos a la vez.
 */
public record BulkProductStatusRequest(
		@NotEmpty List<UUID> ids,
		@NotNull ProductStatus status) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * BulkProductStatusRequest — activar/desactivar en lote.
 * Desactivar un producto = desactivar todas sus variantes.
 */
public record BulkProductStatusRequest(
		@NotEmpty List<UUID> ids,
		@NotNull Boolean isActive) {
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;

/**
 * AdminProductUpdateRequest — campos editables desde el admin. null = no cambiar.
 * Los campos de Softix no aparecen aquí a propósito.
 */
public record AdminProductUpdateRequest(
		ProductStatus status,
		String material,
		String shape,
		String description) {
}

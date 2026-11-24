package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

/**
 * ACTIVE: visible en catálogo y ficha, se puede comprar si hay stock.
 * INACTIVE: solo por URL directa; no aparece en catálogo.
 * HIDDEN: no visible para nadie. Es un estado manual del administrador; la sincronización
 * nunca lo cambia. La falta de respaldo en Softix se representa con {@code softixMissing}.
 */
public enum ProductStatus {
	ACTIVE,
	INACTIVE,
	HIDDEN
}

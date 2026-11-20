package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

/**
 * ACTIVE: visible en catálogo y ficha, se puede comprar si hay stock.
 * INACTIVE: solo por URL directa; no aparece en catálogo.
 * HIDDEN: no visible para nadie. La sincronización lo asigna cuando el SKU ya no está en Softix.
 */
public enum ProductStatus {
	ACTIVE,
	INACTIVE,
	HIDDEN
}

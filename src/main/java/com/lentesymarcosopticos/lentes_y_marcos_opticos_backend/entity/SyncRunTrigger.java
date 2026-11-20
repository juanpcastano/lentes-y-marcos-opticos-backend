package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

/**
 * MANUAL: disparada desde el panel de integración. SCHEDULED: disparada por el sincronizador horario.
 * IMPORT: disparada al confirmar una importación de productos.
 */
public enum SyncRunTrigger {
	MANUAL,
	SCHEDULED,
	IMPORT
}

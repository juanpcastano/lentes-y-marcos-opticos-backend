package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

/**
 * Acción de un evento de sincronización. Los SKUs ignorados no generan eventos.
 */
public enum SyncEventAction {
	CREATED,
	UPDATED,
	HIDDEN,
	RESTORED,
	BLOCKED,
	SUMMARY
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

/**
 * SUCCESS: terminó sin errores por SKU. PARTIAL: terminó aplicando los SKUs válidos y dejó errores por SKU.
 * FAILED: se detuvo sin aplicar cambios.
 */
public enum SyncRunStatus {
	RUNNING,
	SUCCESS,
	PARTIAL,
	FAILED
}

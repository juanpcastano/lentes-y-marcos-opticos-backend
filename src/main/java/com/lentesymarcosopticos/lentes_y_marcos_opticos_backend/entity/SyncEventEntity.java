package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

/**
 * Tipo de entidad al que se refiere un evento de sincronización. RUN son los eventos de resumen o fallo de la ejecución.
 */
public enum SyncEventEntity {
	PRODUCT,
	BRAND,
	CATEGORY,
	RUN
}

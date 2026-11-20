package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.SyncResult;

import lombok.AllArgsConstructor;

/**
 * AdminSoftixSyncController — disparo manual de la sincronización con Softix (solo ADMIN).
 */
@RestController
@RequestMapping("/api/admin/softix/sync")
@AllArgsConstructor
public class AdminSoftixSyncController {

	private final SoftixCatalogSyncService syncService;

	@PostMapping
	public ResponseEntity<SyncResult> sync() {
		return ResponseEntity.ok(syncService.sync());
	}
}

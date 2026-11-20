package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixConnectionStatusDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncPreviewDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixIntegrationService;

import lombok.AllArgsConstructor;

/**
 * AdminIntegrationController — integración con Softix desde el panel (solo ADMIN).
 */
@RestController
@RequestMapping("/api/admin/integration/softix")
@AllArgsConstructor
public class AdminIntegrationController {

	private final SoftixIntegrationService integrationService;

	@GetMapping("/connection")
	public ResponseEntity<SoftixConnectionStatusDto> connection() {
		return ResponseEntity.ok(integrationService.checkConnection());
	}

	@GetMapping("/preview")
	public ResponseEntity<SoftixSyncPreviewDto> preview() {
		return ResponseEntity.ok(integrationService.preview());
	}

	@PostMapping("/sync")
	public ResponseEntity<SoftixSyncRunDto> sync() {
		return ResponseEntity.ok(integrationService.runSync(SyncRunTrigger.MANUAL));
	}

	@GetMapping("/runs")
	public ResponseEntity<PageResponse<SoftixSyncRunDto>> runs(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(integrationService.listRuns(page, size));
	}

	@GetMapping("/runs/{id}")
	public ResponseEntity<SoftixSyncRunDetailDto> run(@PathVariable UUID id) {
		return ResponseEntity.ok(integrationService.getRun(id));
	}
}

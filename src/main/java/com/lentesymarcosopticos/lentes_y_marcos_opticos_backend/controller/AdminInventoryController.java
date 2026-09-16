package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryConfirmResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryPreviewResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.InventoryImportService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixImportService;

import lombok.AllArgsConstructor;

/**
 * AdminInventoryController — importación del Excel de Softix (solo ADMIN).
 * Flujo en dos pasos: preview (clasifica sin persistir) y confirm (aplica con
 * las decisiones del admin). Sin estado en el servidor: el confirm reenvía el
 * archivo junto a las decisiones.
 */
@RestController
@RequestMapping("/api/admin/inventory")
@AllArgsConstructor
public class AdminInventoryController {

	private final SoftixImportService softixImportService;
	private final InventoryImportService inventoryImportService;

	@PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<InventoryPreviewResponse> preview(
			@RequestPart("file") MultipartFile file) {
		validateFile(file);
		return ResponseEntity.ok(softixImportService.preview(file));
	}

	@PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<InventoryConfirmResponse> confirm(
			@RequestPart("file") MultipartFile file,
			@RequestPart(value = "decisions", required = false) Map<String, String> decisions) {
		validateFile(file);
		return ResponseEntity.ok(inventoryImportService.confirm(file,
				decisions == null ? Map.of() : decisions));
	}

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Selecciona un archivo Excel (.xlsx) para importar");
		}
		String name = file.getOriginalFilename() == null ? ""
				: file.getOriginalFilename().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El archivo debe ser un Excel (.xlsx)");
		}
	}
}

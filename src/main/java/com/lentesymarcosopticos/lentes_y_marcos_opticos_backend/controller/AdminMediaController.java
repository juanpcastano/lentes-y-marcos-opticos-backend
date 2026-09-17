package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.MediaImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.MediaAssetDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.StorageService;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AdminMediaService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/admin/media")
@AllArgsConstructor
public class AdminMediaController {

	private static final Set<String> ALLOWED_FOLDERS = Set.of("categories", "brands", "variants", "hero");

	private final StorageService storageService;
	private final AdminMediaService adminMediaService;

	@GetMapping("/gallery")
	public ResponseEntity<List<MediaAssetDto>> gallery() {
		return ResponseEntity.ok(adminMediaService.listGallery());
	}

	@DeleteMapping("/gallery")
	public ResponseEntity<Void> delete(@RequestParam String key,
			@RequestParam(defaultValue = "false") boolean force) {
		adminMediaService.delete(key, force);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{folder}")
	public ResponseEntity<List<MediaImageDto>> list(@PathVariable String folder) {
		String normalizedFolder = normalizeFolder(folder);
		return ResponseEntity.ok(storageService.list(normalizedFolder).stream()
				.map(object -> new MediaImageDto(object.key(), object.url()))
				.toList());
	}

	@PostMapping("/{folder}")
	public ResponseEntity<MediaImageDto> upload(@PathVariable String folder,
			@RequestPart("file") MultipartFile file) {
		String normalizedFolder = normalizeFolder(folder);
		// Staging de variantes: subida inmediata al bucket sin referencia en
		// DB (ver `variants/staging/`). Al guardar el producto/edición, las
		// URLs se adjuntan como VariantImage de las variantes nuevas.
		String targetFolder = "variants".equals(normalizedFolder) ? "variants/staging" : normalizedFolder;
		String url = storageService.store(file, targetFolder);
		String key = url.substring(url.indexOf(targetFolder));
		return ResponseEntity.status(HttpStatus.CREATED).body(new MediaImageDto(key, url));
	}

	private String normalizeFolder(String folder) {
		String normalized = folder.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_FOLDERS.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Carpeta de imágenes no permitida");
		}
		return normalized;
	}
}

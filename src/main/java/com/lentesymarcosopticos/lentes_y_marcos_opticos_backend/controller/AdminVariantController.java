package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ExistingProductImageRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AdminProductService;

import lombok.AllArgsConstructor;

/**
 * AdminVariantController — imágenes de variantes (solo ADMIN).
 * Las imágenes cuelgan de la variante (color), no del producto.
 */
@RestController
@RequestMapping("/api/admin/variants")
@AllArgsConstructor
public class AdminVariantController {

	private final AdminProductService adminProductService;

	@PostMapping("/{variantId}/images")
	public ResponseEntity<ProductImageDto> addImage(@PathVariable UUID variantId,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) Boolean primary) {
		return ResponseEntity.status(201).body(adminProductService.addImage(variantId, file, primary));
	}

	@PostMapping("/{variantId}/images/existing")
	public ResponseEntity<ProductImageDto> addExistingImage(@PathVariable UUID variantId,
			@RequestBody ExistingProductImageRequest request) {
		return ResponseEntity.status(201).body(adminProductService.addExistingImage(variantId,
				request.imageUrl(), request.primary()));
	}

	@DeleteMapping("/{variantId}/images/{imageId}")
	public ResponseEntity<Void> deleteImage(@PathVariable UUID variantId, @PathVariable UUID imageId) {
		adminProductService.deleteImage(variantId, imageId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{variantId}/images/{imageId}/primary")
	public ResponseEntity<ProductImageDto> setPrimaryImage(@PathVariable UUID variantId,
			@PathVariable UUID imageId) {
		return ResponseEntity.ok(adminProductService.setPrimaryImage(variantId, imageId));
	}

	@PutMapping("/{variantId}/images/order")
	public ResponseEntity<List<ProductImageDto>> reorderImages(@PathVariable UUID variantId,
			@RequestBody List<UUID> imageIds) {
		return ResponseEntity.ok(adminProductService.reorderImages(variantId, imageIds));
	}
}

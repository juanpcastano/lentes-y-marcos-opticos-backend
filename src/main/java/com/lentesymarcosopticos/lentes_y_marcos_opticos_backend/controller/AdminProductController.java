package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BulkProductStatusRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ExistingProductImageRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AdminProductService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * AdminProductController — productos y sus imágenes (solo ADMIN). Los productos
 * vienen de Softix; aquí no se crean ni se borran.
 */
@RestController
@RequestMapping("/api/admin/products")
@AllArgsConstructor
public class AdminProductController {

	private final AdminProductService adminProductService;

	@GetMapping
	public ResponseEntity<PageResponse<AdminProductDto>> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) List<String> brands,
			@RequestParam(required = false) List<String> categories,
			@RequestParam(required = false) List<String> materials,
			@RequestParam(required = false) List<String> shapes,
			@RequestParam(required = false) List<String> colors,
			@RequestParam(required = false) List<ProductStatus> statuses,
			@RequestParam(required = false) BigDecimal priceMin,
			@RequestParam(required = false) BigDecimal priceMax,
			@RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size) {
		return ResponseEntity.ok(adminProductService.list(q, brands, categories, materials, shapes,
				colors, statuses, priceMin, priceMax, sort, page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminProductDto> get(@PathVariable UUID id) {
		return ResponseEntity.ok(adminProductService.get(id));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AdminProductDto> update(@PathVariable UUID id,
			@Valid @RequestBody AdminProductUpdateRequest request) {
		return ResponseEntity.ok(adminProductService.update(id, request));
	}

	/** Cambia el estado de varios productos a la vez. */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		adminProductService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/bulk-status")
	public ResponseEntity<Map<String, Object>> bulkStatus(
			@Valid @RequestBody BulkProductStatusRequest request) {
		int updated = adminProductService.bulkSetStatus(request.ids(), request.status());
		return ResponseEntity.ok(Map.of("updated", updated, "status", request.status()));
	}

	@PostMapping("/{id}/images")
	public ResponseEntity<ProductImageDto> addImage(@PathVariable UUID id,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) Boolean primary) {
		return ResponseEntity.status(201).body(adminProductService.addImage(id, file, primary));
	}

	@PostMapping("/{id}/images/existing")
	public ResponseEntity<ProductImageDto> addExistingImage(@PathVariable UUID id,
			@RequestBody ExistingProductImageRequest request) {
		return ResponseEntity.status(201).body(adminProductService.addExistingImage(id,
				request.imageUrl(), request.primary()));
	}

	@DeleteMapping("/{id}/images/{imageId}")
	public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
		adminProductService.deleteImage(id, imageId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/images/{imageId}/primary")
	public ResponseEntity<ProductImageDto> setPrimaryImage(@PathVariable UUID id,
			@PathVariable UUID imageId) {
		return ResponseEntity.ok(adminProductService.setPrimaryImage(id, imageId));
	}

	@PutMapping("/{id}/images/order")
	public ResponseEntity<List<ProductImageDto>> reorderImages(@PathVariable UUID id,
			@RequestBody List<UUID> imageIds) {
		return ResponseEntity.ok(adminProductService.reorderImages(id, imageIds));
	}
}

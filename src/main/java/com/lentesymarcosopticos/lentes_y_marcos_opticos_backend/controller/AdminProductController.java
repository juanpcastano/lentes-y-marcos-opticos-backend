package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.FacetsDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductUpsertRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.AdminProductService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * AdminProductController — CRUD de productos (solo ADMIN)
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
			@RequestParam(required = false) Integer priceMin,
			@RequestParam(required = false) Integer priceMax,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size) {
		return ResponseEntity.ok(adminProductService.list(q, brands, categories, materials, shapes,
				priceMin, priceMax, active, sort, page, size));
	}

	@GetMapping("/facets")
	public ResponseEntity<FacetsDto> facets() {
		return ResponseEntity.ok(adminProductService.facets());
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminProductDto> get(@PathVariable UUID id) {
		return ResponseEntity.ok(adminProductService.get(id));
	}

	@PostMapping
	public ResponseEntity<AdminProductDto> create(@Valid @RequestBody ProductUpsertRequest request) {
		return ResponseEntity.status(201).body(adminProductService.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AdminProductDto> update(@PathVariable UUID id,
			@Valid @RequestBody ProductUpsertRequest request) {
		return ResponseEntity.ok(adminProductService.update(id, request));
	}

	/** Soft delete (is_active = false) */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		adminProductService.deactivate(id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{id}/active")
	public ResponseEntity<Void> setActive(@PathVariable UUID id, @RequestParam boolean active) {
		adminProductService.setActive(id, active);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/images")
	public ResponseEntity<ProductImageDto> addImage(@PathVariable UUID id,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) Boolean primary) {
		return ResponseEntity.status(201).body(adminProductService.addImage(id, file, primary));
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
}

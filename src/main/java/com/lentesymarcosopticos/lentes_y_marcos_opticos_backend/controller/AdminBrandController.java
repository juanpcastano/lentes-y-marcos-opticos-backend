package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BrandDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BrandRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.BrandService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * AdminBrandController — CRUD de marcas (solo ADMIN)
 */
@RestController
@RequestMapping("/api/admin/brands")
@AllArgsConstructor
public class AdminBrandController {

	private final BrandService brandService;

	@GetMapping
	public ResponseEntity<List<BrandDto>> list() {
		return ResponseEntity.ok(brandService.getBrands());
	}

	@PostMapping
	public ResponseEntity<BrandDto> create(@Valid @RequestBody BrandRequest request) {
		return ResponseEntity.status(201).body(brandService.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BrandDto> update(@PathVariable UUID id,
			@Valid @RequestBody BrandRequest request) {
		return ResponseEntity.ok(brandService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		brandService.delete(id);
		return ResponseEntity.noContent().build();
	}
}

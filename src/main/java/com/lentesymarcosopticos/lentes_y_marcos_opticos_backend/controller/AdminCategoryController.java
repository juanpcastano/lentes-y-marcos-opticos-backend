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

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CategoryDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CategoryRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.CategoryService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * AdminCategoryController — CRUD de categorías (solo ADMIN)
 */
@RestController
@RequestMapping("/api/admin/categories")
@AllArgsConstructor
public class AdminCategoryController {

	private final CategoryService categoryService;

	@GetMapping
	public ResponseEntity<List<CategoryDto>> list() {
		return ResponseEntity.ok(categoryService.getCategories());
	}

	@PostMapping
	public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryRequest request) {
		return ResponseEntity.status(201).body(categoryService.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<CategoryDto> update(@PathVariable UUID id,
			@Valid @RequestBody CategoryRequest request) {
		return ResponseEntity.ok(categoryService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		categoryService.delete(id);
		return ResponseEntity.noContent().build();
	}
}

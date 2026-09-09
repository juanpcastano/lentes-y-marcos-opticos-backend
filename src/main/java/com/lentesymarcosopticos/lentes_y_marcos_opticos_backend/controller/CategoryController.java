package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CategoryDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.CategoryService;

import lombok.AllArgsConstructor;

/**
 * CategoryController
 */
@RestController
@RequestMapping("/api/categories")
@AllArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@GetMapping
	public ResponseEntity<List<CategoryDto>> getCategories() {
		return ResponseEntity.ok(categoryService.getCategories());
	}

	@GetMapping("/featured")
	public ResponseEntity<List<CategoryDto>> getFeaturedCategories() {
		return ResponseEntity.ok(categoryService.getFeaturedCategories());
	}
}

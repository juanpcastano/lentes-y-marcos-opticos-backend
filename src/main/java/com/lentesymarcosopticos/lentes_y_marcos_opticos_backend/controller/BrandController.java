package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BrandDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.BrandService;

import lombok.AllArgsConstructor;

/**
 * BrandController
 */
@RestController
@RequestMapping("/api/brands")
@AllArgsConstructor
public class BrandController {

	private final BrandService brandService;

	@GetMapping
	public ResponseEntity<List<BrandDto>> getBrands() {
		return ResponseEntity.ok(brandService.getBrands());
	}

	@GetMapping("/featured")
	public ResponseEntity<List<BrandDto>> getFeaturedBrands() {
		return ResponseEntity.ok(brandService.getFeaturedBrands());
	}
}

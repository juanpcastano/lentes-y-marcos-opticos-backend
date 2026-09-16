package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.FacetsDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductSummaryDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.ProductService;

import lombok.AllArgsConstructor;

/**
 * ProductController
 */
@RestController
@RequestMapping("/api/products")
@AllArgsConstructor
public class ProductController {

	private final ProductService productService;

	@GetMapping
	public ResponseEntity<PageResponse<ProductSummaryDto>> getProducts(
			@RequestParam(required = false) List<String> categories,
			@RequestParam(required = false) List<String> brands,
			@RequestParam(required = false) List<String> materials,
			@RequestParam(required = false) List<String> shapes,
			@RequestParam(required = false) Integer priceMin,
			@RequestParam(required = false) Integer priceMax,
			@RequestParam(required = false) Boolean onSale,
			@RequestParam(required = false) Boolean isNew,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size) {

		return ResponseEntity.ok(productService.getProducts(categories, brands, materials, shapes,
				priceMin, priceMax, sort, onSale, isNew, q, page, size));
	}

	@GetMapping("/facets")
	public ResponseEntity<FacetsDto> getFacets() {
		return ResponseEntity.ok(productService.getFacets());
	}

	@GetMapping("/top-sellers")
	public ResponseEntity<List<ProductSummaryDto>> getTopSellers() {
		return ResponseEntity.ok(productService.getTopSellers());
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProductDetailDto> getProductById(@PathVariable UUID id) {
		return ResponseEntity.ok(productService.getProductById(id));
	}
}

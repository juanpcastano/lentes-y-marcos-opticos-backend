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

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.HeroSlideDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.HeroSlideRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.HeroSlideService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;

/**
 * AdminHeroSlideController — CRUD del hero (solo ADMIN)
 */
@RestController
@RequestMapping("/api/admin/hero-slides")
@AllArgsConstructor
public class AdminHeroSlideController {

	private final HeroSlideService heroSlideService;

	@GetMapping
	public ResponseEntity<List<HeroSlideDto>> list() {
		return ResponseEntity.ok(heroSlideService.listAll());
	}

	@PostMapping
	public ResponseEntity<HeroSlideDto> create(@Valid @RequestBody HeroSlideRequest request) {
		return ResponseEntity.status(201).body(heroSlideService.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<HeroSlideDto> update(@PathVariable UUID id,
			@Valid @RequestBody HeroSlideRequest request) {
		return ResponseEntity.ok(heroSlideService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		heroSlideService.delete(id);
		return ResponseEntity.noContent().build();
	}

	public record ReorderRequest(@NotNull List<UUID> ids) {
	}

	@PutMapping("/reorder")
	public ResponseEntity<List<HeroSlideDto>> reorder(@Valid @RequestBody ReorderRequest request) {
		return ResponseEntity.ok(heroSlideService.reorder(request.ids()));
	}
}

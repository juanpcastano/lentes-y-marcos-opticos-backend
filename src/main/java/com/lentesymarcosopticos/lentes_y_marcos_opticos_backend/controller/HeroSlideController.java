package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.HeroSlideDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.HeroSlideService;

import lombok.AllArgsConstructor;

/**
 * HeroSlideController — slides activos del hero (público)
 */
@RestController
@RequestMapping("/api/hero-slides")
@AllArgsConstructor
public class HeroSlideController {

	private final HeroSlideService heroSlideService;

	@GetMapping
	public ResponseEntity<List<HeroSlideDto>> getActiveSlides() {
		return ResponseEntity.ok(heroSlideService.getActiveSlides());
	}
}

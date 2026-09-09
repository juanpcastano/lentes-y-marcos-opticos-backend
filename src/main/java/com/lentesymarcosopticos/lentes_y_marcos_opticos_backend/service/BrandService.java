package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BrandDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;

import lombok.AllArgsConstructor;

/**
 * BrandService
 */
@Service
@AllArgsConstructor
public class BrandService {

	private final BrandRepository brandRepository;

	@Transactional(readOnly = true)
	public List<BrandDto> getBrands() {
		return brandRepository.findAll().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<BrandDto> getFeaturedBrands() {
		return brandRepository.findByIsFeaturedTrue().stream().map(this::toDto).toList();
	}

	private BrandDto toDto(Brand b) {
		return new BrandDto(b.getId(), b.getName(), b.getTagline(), b.getImageUrl(), b.getIsFeatured());
	}
}

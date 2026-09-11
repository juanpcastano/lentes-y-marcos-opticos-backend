package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BrandDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.BrandRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import lombok.AllArgsConstructor;

/**
 * BrandService
 */
@Service
@AllArgsConstructor
public class BrandService {

	private final BrandRepository brandRepository;
	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public List<BrandDto> getBrands() {
		return brandRepository.findAll().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<BrandDto> getFeaturedBrands() {
		return brandRepository.findByIsFeaturedTrue().stream().map(this::toDto).toList();
	}

	@Transactional
	public BrandDto create(BrandRequest request) {
		String name = request.name().trim();
		if (brandRepository.existsByName(name)) {
			throw new ApiException(HttpStatus.CONFLICT, "Ya existe una marca con ese nombre");
		}
		Brand brand = new Brand();
		brand.setName(name);
		brand.setTagline(request.tagline());
		brand.setImageUrl(request.imageUrl());
		brand.setIsFeatured(Boolean.TRUE.equals(request.isFeatured()));
		return toDto(brandRepository.save(brand));
	}

	@Transactional
	public BrandDto update(UUID id, BrandRequest request) {
		Brand brand = brandRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Marca no encontrada"));
		String name = request.name().trim();
		if (!name.equals(brand.getName()) && brandRepository.existsByName(name)) {
			throw new ApiException(HttpStatus.CONFLICT, "Ya existe una marca con ese nombre");
		}
		brand.setName(name);
		if (request.tagline() != null) {
			brand.setTagline(request.tagline());
		}
		if (request.imageUrl() != null) {
			brand.setImageUrl(request.imageUrl());
		}
		if (request.isFeatured() != null) {
			brand.setIsFeatured(request.isFeatured());
		}
		return toDto(brand);
	}

	@Transactional
	public void delete(UUID id) {
		Brand brand = brandRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Marca no encontrada"));
		if (productRepository.existsByBrand_Id(id)) {
			throw new ApiException(HttpStatus.CONFLICT,
					"No se puede eliminar la marca porque tiene productos asociados; reasígnalos antes de eliminarla");
		}
		brandRepository.delete(brand);
	}

	private BrandDto toDto(Brand b) {
		return new BrandDto(b.getId(), b.getName(), b.getTagline(), b.getImageUrl(), b.getIsFeatured());
	}
}

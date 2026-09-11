package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CategoryDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CategoryRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;

import lombok.AllArgsConstructor;

/**
 * CategoryService
 */
@Service
@AllArgsConstructor
public class CategoryService {

	private final CategoryRepository categoryRepository;

	@Transactional(readOnly = true)
	public List<CategoryDto> getCategories() {
		return categoryRepository.findAll().stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<CategoryDto> getFeaturedCategories() {
		return categoryRepository.findByIsFeaturedTrue().stream().map(this::toDto).toList();
	}

	@Transactional
	public CategoryDto create(CategoryRequest request) {
		String name = request.name().trim();
		if (categoryRepository.existsByName(name)) {
			throw new ApiException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
		}
		Category category = new Category();
		category.setName(name);
		category.setDescription(request.description());
		category.setImageUrl(request.imageUrl());
		category.setIsFeatured(Boolean.TRUE.equals(request.isFeatured()));
		return toDto(categoryRepository.save(category));
	}

	@Transactional
	public CategoryDto update(UUID id, CategoryRequest request) {
		Category category = categoryRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
		String name = request.name().trim();
		if (!name.equals(category.getName()) && categoryRepository.existsByName(name)) {
			throw new ApiException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
		}
		category.setName(name);
		if (request.description() != null) {
			category.setDescription(request.description());
		}
		if (request.imageUrl() != null) {
			category.setImageUrl(request.imageUrl());
		}
		if (request.isFeatured() != null) {
			category.setIsFeatured(request.isFeatured());
		}
		return toDto(category);
	}

	@Transactional
	public void delete(UUID id) {
		Category category = categoryRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
		long productCount = categoryRepository.countProductsById(id);
		if (productCount > 0) {
			throw new ApiException(HttpStatus.CONFLICT,
					"No se puede eliminar la categoría porque tiene " + productCount
							+ (productCount == 1 ? " producto asociado" : " productos asociados")
							+ "; quítala o reasigna esos productos antes de eliminarla");
		}
		categoryRepository.delete(category);
	}

	private CategoryDto toDto(Category c) {
		return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(),
				c.getIsFeatured());
	}
}

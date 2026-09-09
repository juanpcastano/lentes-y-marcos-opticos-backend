package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.CategoryDto;
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

	private CategoryDto toDto(Category c) {
		return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(),
				c.getIsFeatured());
	}
}

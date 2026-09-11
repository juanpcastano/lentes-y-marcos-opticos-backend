package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;

/**
 * BrandRepository
 */
public interface BrandRepository extends JpaRepository<Brand, UUID> {
	List<Brand> findByIsFeaturedTrue();

	boolean existsByName(String name);
}

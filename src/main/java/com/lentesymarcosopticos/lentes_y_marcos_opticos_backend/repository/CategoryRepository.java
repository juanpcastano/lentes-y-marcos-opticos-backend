package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;

/**
 * CategoryRepository
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {
	List<Category> findByIsFeaturedTrue();

	boolean existsByName(String name);

	List<Category> findByNameIn(Collection<String> names);

	@Query("SELECT COUNT(p) FROM Product p JOIN p.categories c WHERE c.id = :categoryId")
	long countProductsById(@Param("categoryId") UUID categoryId);
}

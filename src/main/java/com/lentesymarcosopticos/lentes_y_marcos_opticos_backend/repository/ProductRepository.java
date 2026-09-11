package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;

/**
 * ProductRepository
 */
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

	/**
	 * Fetch details (images, categories, variants) for a page of product ids in a
	 * single query — avoids N+1.
	 */
	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.variants WHERE p.id IN :ids")
	List<Product> findAllWithDetailsByIdIn(@Param("ids") List<UUID> ids);

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.variants WHERE p.id = :id AND p.isActive = true")
	Optional<Product> findActiveDetailById(@Param("id") UUID id);

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.variants WHERE p.id = :id")
	Optional<Product> findDetailById(@Param("id") UUID id);

	boolean existsByBrand_Id(UUID brandId);

	@Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.sku = :sku AND v.product.id <> :productId")
	long countOtherVariantsBySku(@Param("sku") String sku, @Param("productId") UUID productId);

	@Query("SELECT DISTINCT p.material FROM Product p WHERE p.isActive = true AND p.material IS NOT NULL ORDER BY p.material")
	List<String> findDistinctMaterials();

	@Query("SELECT DISTINCT p.material FROM Product p WHERE p.material IS NOT NULL ORDER BY p.material")
	List<String> findAllDistinctMaterials();

	@Query("SELECT DISTINCT p.shape FROM Product p WHERE p.isActive = true AND p.shape IS NOT NULL ORDER BY p.shape")
	List<String> findDistinctShapes();

	@Query("SELECT DISTINCT p.shape FROM Product p WHERE p.shape IS NOT NULL ORDER BY p.shape")
	List<String> findAllDistinctShapes();

	@Query("SELECT MIN(p.basePrice * (1 - COALESCE(p.discountPercentage, 0) / 100.0)), MAX(p.basePrice * (1 - COALESCE(p.discountPercentage, 0) / 100.0)) FROM Product p WHERE p.isActive = true")
	Object[] findPriceBounds();

	@Query("SELECT MIN(p.basePrice * (1 - COALESCE(p.discountPercentage, 0) / 100.0)), MAX(p.basePrice * (1 - COALESCE(p.discountPercentage, 0) / 100.0)) FROM Product p")
	Object[] findAllPriceBounds();
}

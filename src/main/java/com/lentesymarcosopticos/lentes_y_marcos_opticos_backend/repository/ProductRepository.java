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
	 * Fetch details (categories, variants + variant images) for a page of product
	 * ids in a single query — avoids N+1.
	 */
	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.images WHERE p.id IN :ids")
	List<Product> findAllWithDetailsByIdIn(@Param("ids") List<UUID> ids);

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.images")
	List<Product> findAllWithVariantImages();

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.images")
	List<Product> findAllWithVariants();

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.images WHERE p.id = :id")
	Optional<Product> findDetailById(@Param("id") UUID id);

	boolean existsByBrand_Id(UUID brandId);

	@Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.sku = :sku AND v.product.id <> :productId")
	long countOtherVariantsBySku(@Param("sku") String sku, @Param("productId") UUID productId);

	@Query("SELECT DISTINCT p.material FROM Product p WHERE p.material IS NOT NULL ORDER BY p.material")
	List<String> findDistinctMaterials();

	@Query("SELECT DISTINCT p.shape FROM Product p WHERE p.shape IS NOT NULL ORDER BY p.shape")
	List<String> findDistinctShapes();

	@Query("SELECT DISTINCT v.color FROM ProductVariant v WHERE v.isActive = true AND v.color IS NOT NULL ORDER BY v.color")
	List<String> findDistinctColors();

	@Query("SELECT MIN(v.price * (1 - COALESCE(v.discountPercentage, 0) / 100.0)), MAX(v.price * (1 - COALESCE(v.discountPercentage, 0) / 100.0)) FROM ProductVariant v WHERE v.isActive = true")
	List<Object[]> findPriceBounds();
}

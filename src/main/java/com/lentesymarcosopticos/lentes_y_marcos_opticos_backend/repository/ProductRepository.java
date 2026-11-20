package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;

/**
 * ProductRepository
 */
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

	/** Categorías e imágenes de una página de productos, en una sola consulta (evita N+1). */
	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.images WHERE p.id IN :ids")
	List<Product> findAllWithDetailsByIdIn(@Param("ids") List<UUID> ids);

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images")
	List<Product> findAllWithImages();

	@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.images WHERE p.id = :id")
	Optional<Product> findDetailById(@Param("id") UUID id);

	/** Otros colores: mismo marca y referencia, distinto SKU. */
	List<Product> findByBrand_IdAndReferenceAndIdNot(UUID brandId, String reference, UUID id);

	boolean existsByBrand_Id(UUID brandId);

	@Query("SELECT DISTINCT b.name FROM Product p JOIN p.brand b WHERE p.status = :status AND p.stock > 0 ORDER BY b.name")
	List<String> findDistinctBrandNames(@Param("status") ProductStatus status);

	@Query("SELECT DISTINCT c.name FROM Product p JOIN p.categories c WHERE p.status = :status AND p.stock > 0 ORDER BY c.name")
	List<String> findDistinctCategoryNames(@Param("status") ProductStatus status);

	@Query("SELECT DISTINCT p.material FROM Product p WHERE p.material IS NOT NULL AND p.status = :status AND p.stock > 0 ORDER BY p.material")
	List<String> findDistinctMaterials(@Param("status") ProductStatus status);

	@Query("SELECT DISTINCT p.shape FROM Product p WHERE p.shape IS NOT NULL AND p.status = :status AND p.stock > 0 ORDER BY p.shape")
	List<String> findDistinctShapes(@Param("status") ProductStatus status);

	@Query("SELECT DISTINCT p.color FROM Product p WHERE p.color IS NOT NULL AND p.status = :status AND p.stock > 0 ORDER BY p.color")
	List<String> findDistinctColors(@Param("status") ProductStatus status);

	@Query("SELECT MIN(p.price), MAX(p.price) FROM Product p WHERE p.status = :status AND p.stock > 0")
	List<Object[]> findPriceBounds(@Param("status") ProductStatus status);
}

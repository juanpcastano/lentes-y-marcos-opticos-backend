package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;

/**
 * ProductVariantRepository — búsqueda por SKU (llave del import Softix F4)
 * + especificaciones para el catálogo (una fila por variante).
 */
public interface ProductVariantRepository
		extends JpaRepository<ProductVariant, UUID>, JpaSpecificationExecutor<ProductVariant> {
	Optional<ProductVariant> findBySku(String sku);

	List<ProductVariant> findBySkuIn(Collection<String> skus);

	List<ProductVariant> findByProductId(UUID productId);

	long countByProductId(UUID productId);

	@Modifying
	@Query("UPDATE ProductVariant v SET v.isActive = :isActive WHERE v.product.id IN :productIds")
	int updateActiveByProductIds(@Param("productIds") Collection<UUID> productIds,
			@Param("isActive") Boolean isActive);
}

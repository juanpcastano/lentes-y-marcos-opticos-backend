package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}

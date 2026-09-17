package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.VariantImage;

/**
 * VariantImageRepository
 */
public interface VariantImageRepository extends JpaRepository<VariantImage, UUID> {
	List<VariantImage> findByVariantId(UUID variantId);
}

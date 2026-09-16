package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.HeroSlide;

/**
 * HeroSlideRepository
 */
public interface HeroSlideRepository extends JpaRepository<HeroSlide, UUID> {
	List<HeroSlide> findByIsActiveTrueOrderBySortOrderAsc();

	List<HeroSlide> findAllByOrderBySortOrderAsc();
}

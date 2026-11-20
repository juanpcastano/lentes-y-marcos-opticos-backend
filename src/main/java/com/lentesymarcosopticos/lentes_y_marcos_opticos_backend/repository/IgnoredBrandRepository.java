package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.IgnoredBrand;

public interface IgnoredBrandRepository extends JpaRepository<IgnoredBrand, UUID> {
    boolean existsByName(String name);

    void deleteByName(String name);

    List<IgnoredBrand> findAllByOrderByNameAsc();
}
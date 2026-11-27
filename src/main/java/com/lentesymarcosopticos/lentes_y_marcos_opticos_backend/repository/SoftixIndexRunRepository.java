package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixIndexRun;

public interface SoftixIndexRunRepository extends JpaRepository<SoftixIndexRun, UUID> {

	Optional<SoftixIndexRun> findFirstByOrderByStartedAtDesc();
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRun;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunStatus;

/**
 * SoftixSyncRunRepository
 */
public interface SoftixSyncRunRepository extends JpaRepository<SoftixSyncRun, UUID> {
	Page<SoftixSyncRun> findAllByOrderByStartedAtDesc(Pageable pageable);

	boolean existsByStatus(SyncRunStatus status);
}

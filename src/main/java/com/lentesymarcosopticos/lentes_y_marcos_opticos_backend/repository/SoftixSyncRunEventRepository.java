package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRunEvent;

/**
 * SoftixSyncRunEventRepository
 */
public interface SoftixSyncRunEventRepository extends JpaRepository<SoftixSyncRunEvent, Long> {
	List<SoftixSyncRunEvent> findByRunIdOrderByIdAsc(UUID runId);

	@Modifying
	@Transactional
	@Query("DELETE FROM SoftixSyncRunEvent e WHERE e.createdAt < :cutoff")
	int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}

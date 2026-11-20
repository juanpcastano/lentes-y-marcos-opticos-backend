package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRunLog;

/**
 * SoftixSyncRunLogRepository
 */
public interface SoftixSyncRunLogRepository extends JpaRepository<SoftixSyncRunLog, Long> {
	List<SoftixSyncRunLog> findByRunIdOrderByIdAsc(UUID runId);

	@Modifying
	@Transactional
	@Query("DELETE FROM SoftixSyncRunLog l WHERE l.createdAt < :cutoff")
	int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}

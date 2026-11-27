package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixWriteEvent;

public interface SoftixWriteEventRepository extends JpaRepository<SoftixWriteEvent, UUID> {

	@Query(value = "SELECT pg_advisory_xact_lock(hashtext(:subjectKey))", nativeQuery = true)
	void lockSubject(@Param("subjectKey") String subjectKey);

	long countBySubjectKeyAndCreatedAtAfter(String subjectKey, LocalDateTime since);

	long deleteByCreatedAtBefore(LocalDateTime cutoff);
}

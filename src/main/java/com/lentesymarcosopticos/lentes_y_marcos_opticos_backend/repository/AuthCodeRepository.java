package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthCode;

public interface AuthCodeRepository extends JpaRepository<AuthCode, UUID> {

	List<AuthCode> findByEmailAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
			String email, String purpose, LocalDateTime now);

	List<AuthCode> findByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
			String email, String purpose);

	long countByEmailAndPurposeAndCreatedAtAfter(
			String email, String purpose, LocalDateTime since);

	long countByEmailAndCreatedAtAfter(String email, LocalDateTime since);
}

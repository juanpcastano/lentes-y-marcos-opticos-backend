package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthCode;

public interface AuthCodeRepository extends JpaRepository<AuthCode, UUID> {

	List<AuthCode> findByIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
			String identifier, String purpose);

	List<AuthCode> findByIdentifierAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
			String identifier, String purpose, LocalDateTime now);

	long countByIdentifierAndPurposeAndCreatedAtAfter(
			String identifier, String purpose, LocalDateTime since);

	long countByIdentifierAndCreatedAtAfter(String identifier, LocalDateTime since);

	long countByRequestIpAndCreatedAtAfter(String requestIp, LocalDateTime since);
}

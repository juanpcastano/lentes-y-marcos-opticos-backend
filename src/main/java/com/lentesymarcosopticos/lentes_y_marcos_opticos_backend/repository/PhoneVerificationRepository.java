package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.PhoneVerification;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {

	Optional<PhoneVerification> findByIdAndConsumedFalseAndExpiresAtAfter(
			UUID id, LocalDateTime now);
}

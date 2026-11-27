package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixLinkChallenge;

public interface SoftixLinkChallengeRepository extends JpaRepository<SoftixLinkChallenge, UUID> {

	Optional<SoftixLinkChallenge> findByIdAndConsumedFalseAndExpiresAtAfter(
			UUID id, LocalDateTime now);

	Optional<SoftixLinkChallenge> findFirstByPhoneVerificationIdAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
			UUID phoneVerificationId, LocalDateTime now);

	List<SoftixLinkChallenge> findByPhoneE164AndCreatedAtAfter(
			String phoneE164, LocalDateTime since);
}

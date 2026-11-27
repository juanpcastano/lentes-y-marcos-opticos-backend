package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	Optional<AuthSession> findByIdAndRevokedAtIsNullAndExpiresAtAfter(UUID id, LocalDateTime now);
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sesión de refresh con `jti` para permitir revocación. */
@Entity
@Table(name = "auth_sessions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AuthSession {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}

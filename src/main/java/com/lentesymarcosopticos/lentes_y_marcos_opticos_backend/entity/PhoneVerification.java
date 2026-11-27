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

/**
 * Teléfono verificado por OTP pero todavía sin cuenta local. La cuenta se crea
 * solo cuando se completa la vinculación o creación de la ficha en Softix.
 */
@Entity
@Table(name = "phone_verifications")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PhoneVerification {

	@Id
	private UUID id;

	@Column(name = "phone_e164", nullable = false, length = 20)
	private String phoneE164;

	/** Presente solo si existía una cuenta local sin vincular. */
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "consent_version", length = 20)
	private String consentVersion;

	@Column(name = "consent_at")
	private LocalDateTime consentAt;

	@Column(name = "consent_ip", length = 45)
	private String consentIp;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "document_lookup_attempts", nullable = false)
	private Integer documentLookupAttempts = 0;

	@Column(nullable = false)
	private Boolean consumed;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}

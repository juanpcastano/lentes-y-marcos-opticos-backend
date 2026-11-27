package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta local mínima. El perfil vive en Softix y la identidad es el teléfono
 * verificado por OTP.
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "phone_e164", nullable = false, unique = true, length = 20)
	private String phoneE164;

	@Column(name = "phone_verified_at", nullable = false)
	private LocalDateTime phoneVerifiedAt;

	@Column(name = "softix_client_id", nullable = false, unique = true, length = 100)
	private String softixClientId;

	@Column(name = "softix_linked_at", nullable = false)
	private LocalDateTime softixLinkedAt;

	@Column(name = "consent_version", length = 20)
	private String consentVersion;

	@Column(name = "consent_at")
	private LocalDateTime consentAt;

	@Column(name = "consent_ip", length = 45)
	private String consentIp;

	@Column(name = "is_admin")
	private Boolean isAdmin;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;
}

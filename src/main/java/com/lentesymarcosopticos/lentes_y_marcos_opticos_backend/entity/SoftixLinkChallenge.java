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
 * Reto de documento asociado a una verificación de teléfono pendiente. Los
 * candidatos se guardan solo del lado servidor y caducan.
 */
@Entity
@Table(name = "softix_link_challenges")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SoftixLinkChallenge {

	@Id
	private UUID id;

	@Column(name = "phone_verification_id", nullable = false)
	private UUID phoneVerificationId;

	@Column(name = "phone_e164", nullable = false, length = 20)
	private String phoneE164;

	/** Documentos candidatos separados por coma. Nunca se envían al cliente. */
	@Column(name = "candidate_client_ids", nullable = false, length = 2000)
	private String candidateClientIds;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private Boolean consumed;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}

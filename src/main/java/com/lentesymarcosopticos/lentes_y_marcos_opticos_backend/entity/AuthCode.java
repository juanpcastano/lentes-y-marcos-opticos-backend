package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

@Entity
@Table(name = "auth_codes")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AuthCode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Teléfono en E.164. */
	@Column(nullable = false, length = 20)
	private String identifier;

	@Column(nullable = false, length = 20)
	private String channel;

	@Column(nullable = false, length = 20)
	private String purpose;

	@Column(name = "code_hash", nullable = false)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private Boolean consumed;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "request_ip", length = 45)
	private String requestIp;

	@Column(name = "user_agent", length = 255)
	private String userAgent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}

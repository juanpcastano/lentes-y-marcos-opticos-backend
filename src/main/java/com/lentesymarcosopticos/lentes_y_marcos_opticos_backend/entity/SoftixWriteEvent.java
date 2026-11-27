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

/**
 * Intento de escritura en la ficha de Softix. Se registra antes de llamar a
 * Softix para que los intentos fallidos también cuenten en el límite.
 */
@Entity
@Table(name = "softix_write_events")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SoftixWriteEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Teléfono E.164 del usuario; sirve también antes de crear la cuenta. */
	@Column(name = "subject_key", nullable = false, length = 100)
	private String subjectKey;

	@Column(nullable = false, length = 20)
	private String operation;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}

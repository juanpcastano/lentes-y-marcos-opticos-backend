package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SoftixSyncRunEvent — un evento de una ejecución: cambio, aviso, error o resumen de un producto,
 * marca, categoría o de la ejecución. La clave es el SKU para productos y el nombre para marcas y categorías.
 */
@Entity
@Table(name = "softix_sync_run_events")
@NoArgsConstructor
@Getter
@Setter
public class SoftixSyncRunEvent {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 20)
	private SyncEventEntity entityType;

	@Column(name = "entity_key", length = 255)
	private String entityKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SyncEventAction action;

	@Column(nullable = false, length = 10)
	private String level;

	@Column(name = "field_name", length = 30)
	private String field;

	@Column(name = "old_value", columnDefinition = "TEXT")
	private String oldValue;

	@Column(name = "new_value", columnDefinition = "TEXT")
	private String newValue;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public SoftixSyncRunEvent(UUID runId, SyncEventEntity entityType, String entityKey, SyncEventAction action,
			String level, String field, String oldValue, String newValue, String message) {
		this.runId = runId;
		this.entityType = entityType;
		this.entityKey = entityKey;
		this.action = action;
		this.level = level;
		this.field = field;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.message = message;
		this.createdAt = LocalDateTime.now();
	}
}

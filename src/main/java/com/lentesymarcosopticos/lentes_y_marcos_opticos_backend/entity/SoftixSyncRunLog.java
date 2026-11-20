package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SoftixSyncRunLog — evento de una sincronización: resumen, SKU omitido u oculto, o error.
 */
@Entity
@Table(name = "softix_sync_run_logs")
@NoArgsConstructor
@Getter
@Setter
public class SoftixSyncRunLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(nullable = false, length = 10)
	private String level;

	@Column(length = 50)
	private String sku;

	@Column(name = "field_name", length = 30)
	private String fieldName;

	@Column(name = "old_value", columnDefinition = "TEXT")
	private String oldValue;

	@Column(name = "new_value", columnDefinition = "TEXT")
	private String newValue;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public SoftixSyncRunLog(UUID runId, String level, String sku, String message) {
		this(runId, level, sku, null, null, null, message);
	}

	public SoftixSyncRunLog(UUID runId, String level, String sku, String fieldName, String oldValue,
			String newValue, String message) {
		this.runId = runId;
		this.level = level;
		this.sku = sku;
		this.fieldName = fieldName;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.message = message;
		this.createdAt = LocalDateTime.now();
	}
}

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
 * SoftixSyncRun — registro de una ejecución de sincronización con Softix.
 */
@Entity
@Table(name = "softix_sync_runs")
@NoArgsConstructor
@Getter
@Setter
public class SoftixSyncRun {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false, length = 20)
	private SyncRunTrigger triggerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SyncRunStatus status;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "updated_count", nullable = false)
	private Integer updatedCount = 0;

	@Column(name = "hidden_count", nullable = false)
	private Integer hiddenCount = 0;

	@Column(name = "error_count", nullable = false)
	private Integer errorCount = 0;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	public SoftixSyncRun(SyncRunTrigger triggerType, LocalDateTime startedAt) {
		this.triggerType = triggerType;
		this.status = SyncRunStatus.RUNNING;
		this.startedAt = startedAt;
	}
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

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

/** Registro independiente de cada refresco del índice de clientes. */
@Entity
@Table(name = "softix_index_runs")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SoftixIndexRun {

	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_OK = "OK";
	public static final String STATUS_PARTIAL = "PARTIAL";
	public static final String STATUS_FAILED = "FAILED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "total_records")
	private Integer totalRecords;

	@Column(name = "unique_clients")
	private Integer uniqueClients;

	@Column(name = "indexed_phones")
	private Integer indexedPhones;

	@Column(length = 500)
	private String message;
}

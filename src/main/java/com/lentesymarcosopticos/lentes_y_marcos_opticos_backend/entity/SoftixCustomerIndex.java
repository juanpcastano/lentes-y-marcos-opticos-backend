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

/**
 * Mapeo local teléfono -> cliente Softix. No guarda nombre, documento ni
 * dirección. Un teléfono puede tener varias fichas, por eso la clave única es
 * la pareja (teléfono, cliente).
 */
@Entity
@Table(name = "softix_customer_index")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SoftixCustomerIndex {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "phone_e164", nullable = false, length = 20)
	private String phoneE164;

	@Column(name = "softix_client_id", nullable = false, length = 100)
	private String softixClientId;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;
}

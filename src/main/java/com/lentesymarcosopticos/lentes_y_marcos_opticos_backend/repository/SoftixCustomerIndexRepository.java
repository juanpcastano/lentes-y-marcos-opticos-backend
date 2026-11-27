package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixCustomerIndex;

public interface SoftixCustomerIndexRepository extends JpaRepository<SoftixCustomerIndex, UUID> {

	List<SoftixCustomerIndex> findByPhoneE164OrderBySoftixClientId(String phoneE164);

	Optional<SoftixCustomerIndex> findBySoftixClientId(String softixClientId);

	long countByPhoneE164(String phoneE164);
}

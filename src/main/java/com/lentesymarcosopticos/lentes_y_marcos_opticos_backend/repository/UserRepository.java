package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByPhoneE164(String phoneE164);

	boolean existsByPhoneE164(String phoneE164);

	boolean existsBySoftixClientId(String softixClientId);
}

package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;

/**
 * UserRepository
 */
public interface UserRepository extends JpaRepository<User, UUID> {
	public Optional<User> findByEmail(String email);

	public Optional<User> findByGoogleSub(String googleSub);

	public boolean existsByEmail(String email);
}

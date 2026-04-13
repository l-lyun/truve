package com.truve.platform.musical.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.user.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByUserId(UUID userId);

	Optional<User> findByUserId(UUID userId);

	List<User> findByUserIdIn(Collection<UUID> userIds);
}

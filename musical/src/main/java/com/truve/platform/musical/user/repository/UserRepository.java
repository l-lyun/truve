package com.truve.platform.musical.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.user.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	boolean existsByUserId(Long userId);
}

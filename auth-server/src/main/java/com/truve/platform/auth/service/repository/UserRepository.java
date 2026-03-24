package com.truve.platform.auth.service.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.auth.service.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByPublicId(UUID publicId);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	default User findByEmailOrThrow(String email) {
		return findByEmail(email).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_EMAIL)
		);
	}
}

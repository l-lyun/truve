package com.truve.platform.musical.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.service.domain.entity.Musical;

public interface MusicalRepository extends JpaRepository<Musical, Long> {

	default Musical findByIdOrThrow(Long musicalId) {
		return findById(musicalId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_MUSICAL)
		);
	}
}

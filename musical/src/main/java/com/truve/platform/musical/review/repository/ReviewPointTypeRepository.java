package com.truve.platform.musical.review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.review.domain.constant.ReviewPointName;
import com.truve.platform.musical.review.domain.entity.ReviewPointType;

public interface ReviewPointTypeRepository extends JpaRepository<ReviewPointType, Long> {
	Optional<ReviewPointType> findByPoint(ReviewPointName name);
}

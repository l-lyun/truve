package com.truve.platform.musical.review.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.review.domain.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByUserIdAndShowId(UUID userId,  Long showId);

	Page<Review> findByShowIdAndDeletedAtIsNull(Long showId, Pageable pageable);
	Page<Review> findByShowIdAndDeletedAtIsNullAndIsPositiveTrue(Long showId, Pageable pageable);
	Page<Review> findByShowIdAndDeletedAtIsNullAndIsPositiveFalse(Long showId, Pageable pageable);

	long countByShowIdAndDeletedAtIsNull(Long showId);
	long countByShowIdAndDeletedAtIsNullAndIsPositiveTrue(Long showId);


}

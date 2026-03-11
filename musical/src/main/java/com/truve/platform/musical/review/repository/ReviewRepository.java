package com.truve.platform.musical.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.review.domain.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}

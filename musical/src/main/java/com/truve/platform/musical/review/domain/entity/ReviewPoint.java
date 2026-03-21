package com.truve.platform.musical.review.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Tag(name = "review_point")
public class ReviewPoint extends BaseEntity {

	@ManyToOne
	@JoinColumn(name = "review_id")
	private Review review;

	@ManyToOne
	@JoinColumn(name = "review_point_type_id")
	private ReviewPointType reviewPointType;

	@Builder
	private ReviewPoint(Review review, ReviewPointType reviewPointType) {
		this.review = review;
		this.reviewPointType = reviewPointType;
	}

	public static ReviewPoint create(Review review, ReviewPointType reviewPointType) {
		return ReviewPoint.builder()
			.review(review)
			.reviewPointType(reviewPointType)
			.build();
	}
}

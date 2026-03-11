package com.truve.platform.musical.review.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.truve.platform.musical.review.domain.constant.ReviewPointCategory;

class ReviewPointTypeTest {

	@Test
	@DisplayName("ReviewPointType을 생성한다.")
	void 리뷰포인트타입_생성_성공() {
		ReviewPointType reviewPointType = ReviewPointType.builder()
			.category(ReviewPointCategory.CHARM)
			.name("감동")
			.code("TOUCHED")
			.order(1L)
			.build();

		assertAll(
			() -> assertThat(reviewPointType.getCategory()).isEqualTo(ReviewPointCategory.CHARM),
			() -> assertThat(reviewPointType.getName()).isEqualTo("감동"),
			() -> assertThat(reviewPointType.getCode()).isEqualTo("TOUCHED"),
			() -> assertThat(reviewPointType.getOrder()).isEqualTo(1L)
		);
	}
}

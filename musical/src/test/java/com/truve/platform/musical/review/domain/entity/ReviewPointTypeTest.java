package com.truve.platform.musical.review.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.musical.review.domain.constant.ReviewPointCategory;
import com.truve.platform.musical.review.domain.constant.ReviewPointName;

class ReviewPointTypeTest {

	@Test
	@DisplayName("ReviewPointType을 생성한다.")
	void 리뷰포인트타입_생성_성공() {
		ReviewPointType reviewPointType = new ReviewPointType();
		ReflectionTestUtils.setField(reviewPointType, "category", ReviewPointCategory.EMOTION);
		ReflectionTestUtils.setField(reviewPointType, "point", ReviewPointName.TOUCHING);
		ReflectionTestUtils.setField(reviewPointType, "code", "E05");
		ReflectionTestUtils.setField(reviewPointType, "order", 5L);

		assertAll(
			() -> assertThat(reviewPointType.getCategory()).isEqualTo(ReviewPointCategory.EMOTION),
			() -> assertThat(reviewPointType.getPoint()).isEqualTo(ReviewPointName.TOUCHING),
			() -> assertThat(reviewPointType.getCode()).isEqualTo("E05"),
			() -> assertThat(reviewPointType.getOrder()).isEqualTo(5L),
			() -> assertThat(reviewPointType.isEmotionPoint()).isTrue(),
			() -> assertThat(reviewPointType.isCharmPoint(ReviewPointName.STORY)).isFalse()
		);
	}
}

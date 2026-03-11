package com.truve.platform.musical.review.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewTest {

	@Test
	@DisplayName("Review를 생성한다.")
	void 리뷰_생성_성공() {
		LocalDateTime watchedAt = LocalDateTime.of(2026, 3, 11, 0, 0);

		Review review = Review.builder()
			.showId(10L)
			.userId(20L)
			.content("테스트")
			.isPositive(true)
			.watchedAt(watchedAt)
			.build();

		assertAll(
			() -> assertThat(review.getShowId()).isEqualTo(10L),
			() -> assertThat(review.getUserId()).isEqualTo(20L),
			() -> assertThat(review.getContent()).isEqualTo("테스트"),
			() -> assertThat(review.getIsPositive()).isTrue(),
			() -> assertThat(review.getWatchedAt()).isEqualTo(watchedAt),
			() -> assertThat(review.getDeletedAt()).isNull()
		);
	}
}

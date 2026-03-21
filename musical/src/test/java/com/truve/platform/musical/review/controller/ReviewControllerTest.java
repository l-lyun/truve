package com.truve.platform.musical.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.review.domain.constant.ReviewPointName;
import com.truve.platform.musical.review.dto.ReviewRequest;
import com.truve.platform.musical.review.service.ReviewService;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

	@Mock
	private ReviewService reviewService;

	@InjectMocks
	private ReviewController reviewController;

	@Test
	@DisplayName("리뷰 생성 요청을 서비스에 위임하고 성공 응답을 반환한다.")
	void 리뷰_생성_성공() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Long showId = 1L;
		ReviewRequest.Create request = new ReviewRequest.Create(
			true,
			List.of(ReviewPointName.IMMERSION, ReviewPointName.TOUCHING),
			List.of(ReviewPointName.STORY, ReviewPointName.ACTING),
			"재밌게 봤어요"
		);

		ApiResult<Void> response = reviewController.create(userId, showId, request);

		verify(reviewService).create(userId, showId, request);
		assertThat(response.getCode()).isEqualTo("ok");
		assertThat(response.getMessage()).isEqualTo("성공");
		assertThat(response.getData()).isNull();
	}
}

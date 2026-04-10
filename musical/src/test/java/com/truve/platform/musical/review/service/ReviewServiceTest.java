package com.truve.platform.musical.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.review.dao.ReviewPointCountDao;
import com.truve.platform.musical.review.domain.constant.ReviewPointName;
import com.truve.platform.musical.review.domain.constant.ReviewSortType;
import com.truve.platform.musical.review.domain.entity.Review;
import com.truve.platform.musical.review.domain.entity.ReviewPoint;
import com.truve.platform.musical.review.domain.entity.ReviewPointType;
import com.truve.platform.musical.review.dto.ReviewRequest;
import com.truve.platform.musical.review.repository.ReviewPointRepository;
import com.truve.platform.musical.review.repository.ReviewPointTypeRepository;
import com.truve.platform.musical.review.repository.ReviewRepository;
import com.truve.platform.musical.user.domain.entity.User;
import com.truve.platform.musical.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

	@Mock
	private ReviewRepository reviewRepository;
	@Mock
	private ReviewPointTypeRepository reviewPointTypeRepository;
	@Mock
	private ReviewPointRepository reviewPointRepository;
	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private ReviewService reviewService;

	@Test
	@DisplayName("리뷰 저장에 성공하면 카테고리를 저장한다.")
	void 리뷰_저장_성공() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Long showId = 1L;
		ReviewRequest.Create request = new ReviewRequest.Create(
			true,
			List.of(ReviewPointName.IMMERSION, ReviewPointName.TOUCHING),
			List.of(ReviewPointName.STORY, ReviewPointName.ACTING),
			"재밌게 봤어요",
			"최고였어요"
		);

		ReviewPointType immersion = reviewPointType(ReviewPointName.IMMERSION);
		ReviewPointType touching = reviewPointType(ReviewPointName.TOUCHING);
		ReviewPointType story = reviewPointType(ReviewPointName.STORY);
		ReviewPointType acting = reviewPointType(ReviewPointName.ACTING);

		given(reviewRepository.existsByUserIdAndShowId(userId, showId)).willReturn(false);
		given(reviewPointTypeRepository.findByPoint(ReviewPointName.IMMERSION)).willReturn(Optional.of(immersion));
		given(reviewPointTypeRepository.findByPoint(ReviewPointName.TOUCHING)).willReturn(Optional.of(touching));
		given(reviewPointTypeRepository.findByPoint(ReviewPointName.STORY)).willReturn(Optional.of(story));
		given(reviewPointTypeRepository.findByPoint(ReviewPointName.ACTING)).willReturn(Optional.of(acting));
		given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> {
			Review review = invocation.getArgument(0);
			ReflectionTestUtils.setField(review, "id", 100L);
			return review;
		});

		reviewService.create(userId, showId, request);

		ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
		ArgumentCaptor<List<ReviewPoint>> reviewPointsCaptor = ArgumentCaptor.forClass(List.class);

		verify(reviewRepository).save(reviewCaptor.capture());
		verify(reviewPointRepository).saveAll(reviewPointsCaptor.capture());

		Review savedReview = reviewCaptor.getValue();
		List<ReviewPoint> savedPoints = reviewPointsCaptor.getValue();

		assertAll(
			() -> assertThat(savedReview.getShowId()).isEqualTo(showId),
			() -> assertThat(savedReview.getUserId()).isEqualTo(userId),
			() -> assertThat(savedReview.getTitle()).isEqualTo("최고였어요"),
			() -> assertThat(savedReview.getContent()).isEqualTo("재밌게 봤어요"),
			() -> assertThat(savedReview.getIsPositive()).isTrue(),
			() -> assertThat(savedReview.getWatchedAt()).isNotNull(),
			() -> assertThat(savedPoints).hasSize(4),
			() -> assertThat(savedPoints).extracting(point -> point.getReviewPointType().getPoint())
				.containsExactly(ReviewPointName.IMMERSION, ReviewPointName.TOUCHING, ReviewPointName.STORY, ReviewPointName.ACTING)
		);
	}

	@Test
	@DisplayName("이미 같은 공연의 리뷰가 존재하면 예외가 발생한다.")
	void 리뷰_중복_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Long showId = 1L;
		ReviewRequest.Create request = new ReviewRequest.Create(
			true,
			List.of(ReviewPointName.IMMERSION),
			List.of(ReviewPointName.STORY),
			"재밌게 봤어요",
			"중복 테스트"
		);

		given(reviewRepository.existsByUserIdAndShowId(userId, showId)).willReturn(true);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> reviewService.create(userId, showId, request)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXIST_REVIEW);
		verify(reviewRepository, never()).save(any());
		verify(reviewPointRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("감정 포인트 자리에 매력 포인트가 들어오면 예외가 발생한다.")
	void 감정포인트_검증_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		ReviewRequest.Create request = new ReviewRequest.Create(
			true,
			List.of(ReviewPointName.STORY),
			List.of(ReviewPointName.ACTING),
			"재밌게 봤어요",
			"포인트 검증"
		);

		given(reviewRepository.existsByUserIdAndShowId(userId, 1L)).willReturn(false);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> reviewService.create(userId, 1L, request)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_EMOTION_POINT);
		verify(reviewRepository, never()).save(any());
	}

	@Test
	@DisplayName("리뷰 메타 조회 시 포인트 집계와 점수를 반환한다.")
	void 리뷰_메타_조회_성공() {
		// given
		Long showId = 1L;
		given(reviewRepository.countByShowIdAndDeletedAtIsNull(showId)).willReturn(20L);
		given(reviewRepository.countByShowIdAndDeletedAtIsNullAndIsPositiveTrue(showId)).willReturn(15L);
		given(reviewPointRepository.countPointsByShowId(showId)).willReturn(List.of(
			new ReviewPointCountDao(ReviewPointName.STORY, 10L),
			new ReviewPointCountDao(ReviewPointName.IMMERSION, 8L)
		));

		// when
		var response = reviewService.getReviewMeta(showId);

		// then
		assertAll(
			() -> assertThat(response.getShowId()).isEqualTo(showId),
			() -> assertThat(response.getWeeklyRanking()).isEqualTo(1L),
			() -> assertThat(response.getTruveScore()).isEqualTo(75L),
			() -> assertThat(response.getCharmPointScores()).isNotEmpty(),
			() -> assertThat(response.getEmotionPointScores()).isNotEmpty()
		);
	}

	@Test
	@DisplayName("리뷰 목록 조회는 최신순(createdAt desc, id desc)으로 정렬 요청한다.")
	void 리뷰목록_최신순_정렬요청() {
		// given
		Long showId = 1L;
		Paging paging = new Paging(1, 10);
		Review review = Review.builder()
			.showId(showId)
			.userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
			.title("리뷰 제목")
			.content("리뷰 내용")
			.isPositive(true)
			.watchedAt(java.time.LocalDateTime.now())
			.build();
		ReflectionTestUtils.setField(review, "id", 1L);
		ReflectionTestUtils.setField(review, "createdAt", java.time.LocalDateTime.now());

		User user = mock(User.class);
		when(user.getNickname()).thenReturn("tester");

		given(reviewRepository.findByShowIdAndDeletedAtIsNull(any(Long.class), any(Pageable.class)))
			.willReturn(new PageImpl<>(List.of(review)));
		given(userRepository.findByUserId(review.getUserId())).willReturn(Optional.of(user));

		// when
		Page<?> result = reviewService.getReviews(showId, ReviewSortType.LATEST, paging);

		// then
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(reviewRepository).findByShowIdAndDeletedAtIsNull(eq(showId), pageableCaptor.capture());

		Pageable pageable = pageableCaptor.getValue();
		assertAll(
			() -> assertThat(result.getContent()).hasSize(1),
			() -> assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull(),
			() -> assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue(),
			() -> assertThat(pageable.getSort().getOrderFor("id")).isNotNull(),
			() -> assertThat(pageable.getSort().getOrderFor("id").isDescending()).isTrue()
		);
	}

	private ReviewPointType reviewPointType(ReviewPointName point) {
		ReviewPointType reviewPointType = mock(ReviewPointType.class);
		when(reviewPointType.getPoint()).thenReturn(point);
		return reviewPointType;
	}
}

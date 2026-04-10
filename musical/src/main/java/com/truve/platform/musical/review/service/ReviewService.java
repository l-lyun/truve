package com.truve.platform.musical.review.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.response.Paging;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.musical.review.dao.ReviewPointCountDao;
import com.truve.platform.musical.review.domain.constant.ReviewPointCategory;
import com.truve.platform.musical.review.domain.constant.ReviewPointName;
import com.truve.platform.musical.review.domain.constant.ReviewSortType;
import com.truve.platform.musical.review.domain.entity.Review;
import com.truve.platform.musical.review.domain.entity.ReviewPoint;
import com.truve.platform.musical.review.domain.entity.ReviewPointType;
import com.truve.platform.musical.review.dto.ReviewRequest;
import com.truve.platform.musical.review.dto.ReviewResponse;
import com.truve.platform.musical.review.repository.ReviewPointRepository;
import com.truve.platform.musical.review.repository.ReviewPointTypeRepository;
import com.truve.platform.musical.review.repository.ReviewRepository;
import com.truve.platform.musical.user.domain.entity.User;
import com.truve.platform.musical.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

	private static final long MINIMUM_REVIEW_COUNT_FOR_SCORE = 20;

	private final ReviewRepository reviewRepository;
	private final ReviewPointTypeRepository reviewPointTypeRepository;
	private final ReviewPointRepository reviewPointRepository;
	private final UserRepository userRepository;

	@Transactional
	public void create(UUID userId, Long showId, ReviewRequest.Create request) {

		Preconditions.validate(
			!reviewRepository.existsByUserIdAndShowId(userId, showId),
			ErrorCode.ALREADY_EXIST_REVIEW
		);

		List<ReviewPointType> emotionPoints = getEmotionPoints(request.getEmotionPoints());
		List<ReviewPointType> charmPoints = getCharmPoints(request.getCharmPoints());


		Review review = Review.builder().
			showId(showId).
			userId(userId).
			title(request.getTitle()).
			content(request.getContent()).
			isPositive(request.getIsPositive()).
			// TODO: 티켓 및 예매 완료 시 연동
				watchedAt(LocalDateTime.now()).
			build();

		Review savedReview =  reviewRepository.save(review);

		List<ReviewPoint> reviewPoints = new ArrayList<>();


		emotionPoints.forEach(emotionPoint -> {
			reviewPoints.add(ReviewPoint.create(savedReview, emotionPoint));
		});

		charmPoints.forEach(charmPoint -> {
			reviewPoints.add(ReviewPoint.create(savedReview, charmPoint));
		});

		reviewPointRepository.saveAll(reviewPoints);
	}

	public ReviewResponse.Search getReviewMeta(Long showId) {
		long reviewCount = reviewRepository.countByShowIdAndDeletedAtIsNull(showId);
		long positiveReviewCount = reviewRepository.countByShowIdAndDeletedAtIsNullAndIsPositiveTrue(showId);

		long truveScore = reviewCount < 20 ? 0 : Math.round(positiveReviewCount * 100) / reviewCount;

		Map<ReviewPointName, Long> pointCounts = reviewPointRepository.countPointsByShowId(showId)
			.stream()
			.collect(Collectors.toMap(
				ReviewPointCountDao::getPoint,
				ReviewPointCountDao::getCount
			));

		List<ReviewResponse.PointScore> charmPointScores = toPointScores(ReviewPointCategory.CHARM, pointCounts, reviewCount);
		List<ReviewResponse.PointScore> emotionPointScores = toPointScores(ReviewPointCategory.EMOTION, pointCounts, reviewCount);

		// TODO: 일간 랭킹 예매순 추가 후 반영
		return ReviewResponse.Search.create(
			1L,
			truveScore,
			showId,
			charmPointScores,
			emotionPointScores
		);
	}

	public Page<ReviewResponse.ReviewItem> getReviews(Long showId, ReviewSortType sort, Paging paging) {
		Pageable pageable = PageRequest.of(
			paging.getPage() - 1,
			paging.getSize(),
			Sort.by(
				Sort.Order.desc("createdAt"),
				Sort.Order.desc("id")
			)
		);
		Page<Review> reviews = getSortedReviews(showId, sort, pageable);
		return reviews.map(this::toReviewItem);
	}

	private List<ReviewPointType> getCharmPoints(List<ReviewPointName> charmPoints) {
		return charmPoints.stream()
			.map(charmPoint -> {
				Preconditions.validate(charmPoint.isCharmPoint(), ErrorCode.NOT_CHARM_POINT);
				return reviewPointTypeRepository.findByPoint(charmPoint)
					.orElseThrow(() -> new CustomException(ErrorCode.NOT_CHARM_POINT));
			}).toList();
	}

	private List<ReviewPointType> getEmotionPoints(List<ReviewPointName> emotionPoints) {
		return emotionPoints.stream()
			.map(emotionPoint -> {
				Preconditions.validate(emotionPoint.isEmotionPoint(), ErrorCode.NOT_EMOTION_POINT);
				return reviewPointTypeRepository.findByPoint(emotionPoint)
					.orElseThrow(() -> new CustomException(ErrorCode.NOT_EMOTION_POINT));
			}).toList();
	}


	private Page<Review> getSortedReviews(Long showId, ReviewSortType sort, Pageable pageable) {
		return switch (sort) {
			case LATEST -> reviewRepository.findByShowIdAndDeletedAtIsNull(showId, pageable);
			case POSITIVE -> reviewRepository.findByShowIdAndDeletedAtIsNullAndIsPositiveTrue(showId, pageable);
			case NEGATIVE -> reviewRepository.findByShowIdAndDeletedAtIsNullAndIsPositiveFalse(showId, pageable);
		};
	}

	private List<ReviewResponse.PointScore> toPointScores(
		ReviewPointCategory category,
		Map<ReviewPointName, Long> pointCounts,
		long reviewCount
	) {
		if (reviewCount < MINIMUM_REVIEW_COUNT_FOR_SCORE) {
			return List.of();
		}

		return Arrays.stream(ReviewPointName.values())
			.filter(point -> point.getCategory() == category)
			.map(point -> ReviewResponse.PointScore.create(
				point,
				point.getLabel(),
				Math.round(pointCounts.getOrDefault(point, 0L) * 100.0 / reviewCount)
			))
			.toList();
	}

	private ReviewResponse.ReviewItem toReviewItem(Review review){

		User user = userRepository.findByUserId(review.getUserId()).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_REVIEW_USER)
		);

		return ReviewResponse.ReviewItem.create(
			review.getId(),
			review.getUserId(),
			user.getNickname(),
			review.getTitle(),
			review.getContent(),
			review.getIsPositive(),
			review.getCreatedAt()
		);
	}

}

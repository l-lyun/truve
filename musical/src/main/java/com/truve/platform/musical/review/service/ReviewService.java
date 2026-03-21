package com.truve.platform.musical.review.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.musical.review.domain.constant.ReviewPointName;
import com.truve.platform.musical.review.domain.entity.Review;
import com.truve.platform.musical.review.domain.entity.ReviewPoint;
import com.truve.platform.musical.review.domain.entity.ReviewPointType;
import com.truve.platform.musical.review.dto.ReviewRequest;
import com.truve.platform.musical.review.repository.ReviewPointRepository;
import com.truve.platform.musical.review.repository.ReviewPointTypeRepository;
import com.truve.platform.musical.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final ReviewPointTypeRepository reviewPointTypeRepository;
	private final ReviewPointRepository reviewPointRepository;


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

}

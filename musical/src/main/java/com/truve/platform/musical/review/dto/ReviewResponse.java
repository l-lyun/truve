package com.truve.platform.musical.review.dto;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.truve.platform.musical.review.domain.constant.ReviewPointName;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class ReviewResponse {

	@Getter
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static class ReviewItem {
		private Long reviewId;
		private UUID userId;
		private String userNickname;
		private String title;
		private String content;
		private boolean isPositive;
		private LocalDateTime createdAt;

		public static ReviewItem create(Long reviewId, UUID userId, String userNickname, String title, String content, boolean isPositive, LocalDateTime createdAt) {
			return new ReviewItem(reviewId, userId, userNickname, title, content, isPositive, createdAt);
		}

	}

	@Getter
	@AllArgsConstructor(access =  AccessLevel.PRIVATE)
	public static class PointScore {
		private ReviewPointName name;
		private String label;
		private Long score;

		public static PointScore create(ReviewPointName name, String label, Long score) {
			return new PointScore(name, label, score);
		}
	}

	@Getter
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Search {
		private Long weeklyRanking;
		private Long truveScore;
		private Long showId;
		private List<PointScore> charmPointScores;
		private List<PointScore> emotionPointScores;

		public static Search create(Long weeklyRanking, Long truveScore, Long showId, List<PointScore> charmPointScores, List<PointScore> emotionPointScores) {
			return new  Search(weeklyRanking, truveScore, showId, charmPointScores, emotionPointScores);
		}
	}


}

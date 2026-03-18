package com.truve.platform.musical.show.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "show_stats")
public class ShowStats extends BaseEntity {

	@Column(name = "show_id", nullable = false, unique = true)
	private Long showId;

	@Column(name = "daily_rank")
	private Integer dailyRank;

	@Column(name = "weekly_rank")
	private Integer weeklyRank;

	@Column(name = "review_count")
	private Long reviewCount;

	@Builder
	private ShowStats(Long showId, Integer dailyRank, Integer weeklyRank, Long reviewCount) {
		this.showId = showId;
		this.dailyRank = dailyRank;
		this.weeklyRank = weeklyRank;
		this.reviewCount = reviewCount;
	}
}

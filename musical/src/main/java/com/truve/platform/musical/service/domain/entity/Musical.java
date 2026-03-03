package com.truve.platform.musical.service.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "musicals")
public class Musical extends BaseEntity {

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String posterUrl;

	@Column(nullable = false)
	private String stage;

	@Column(nullable = false)
	private String runningTime;

	@Column(nullable = false)
	private String ageLimit;

	@Column(nullable = false)
	private String priceInfo;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	@Column(nullable = false)
	private LocalDateTime openAt;

	@Column(nullable = false)
	private Double ratingAverage;

	@Column(nullable = false)
	private Integer weeklyRank;

	@Column(nullable = false)
	private Integer reviewCount;

	@Column(nullable = false)
	private String timeInfo;

	@Column(nullable = false)
	private String noticeUrl;

	@Column(nullable = false)
	private String detailsUrl;

	@OneToMany(mappedBy = "musical")
	private List<MusicalSchedule> schedules = new ArrayList<>();

	@OneToMany(mappedBy = "musical")
	private List<MusicalSeatPrice> seatPrices = new ArrayList<>();

	@Builder
	private Musical(
		String title,
		String posterUrl,
		String stage,
		String runningTime,
		String ageLimit,
		String priceInfo,
		LocalDate startDate,
		LocalDate endDate,
		LocalDateTime openAt,
		Double ratingAverage,
		Integer weeklyRank,
		Integer reviewCount,
		String timeInfo,
		String noticeUrl,
		String detailsUrl
	) {
		this.title = title;
		this.posterUrl = posterUrl;
		this.stage = stage;
		this.runningTime = runningTime;
		this.ageLimit = ageLimit;
		this.priceInfo = priceInfo;
		this.startDate = startDate;
		this.endDate = endDate;
		this.openAt = openAt;
		this.ratingAverage = ratingAverage;
		this.weeklyRank = weeklyRank;
		this.reviewCount = reviewCount;
		this.timeInfo = timeInfo;
		this.noticeUrl = noticeUrl;
		this.detailsUrl = detailsUrl;
	}
}

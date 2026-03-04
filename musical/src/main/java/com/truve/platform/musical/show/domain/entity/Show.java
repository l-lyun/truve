package com.truve.platform.musical.show.domain.entity;

import java.time.LocalDateTime;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.seat.domain.entity.Venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "shows")

public class Show extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "venue_id", nullable = false)
	private Venue venue;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	private Integer runtimeMin;

	private Integer ageLimit;

	@Column(nullable = false, length = 500)
	private String posterUrl;

	@Column(length = 500)
	private String noticeUrl;

	@Column(name = "start_time")
	private LocalDateTime startTime;

	@Column(name = "end_time")
	private LocalDateTime endTime;

	@Builder
	private Show(
		Venue venue,
		String title,
		String description,
		Integer runtimeMin,
		Integer ageLimit,
		String posterUrl,
		String noticeUrl,
		LocalDateTime startTime,
		LocalDateTime endTime
	) {
		this.venue = venue;
		this.title = title;
		this.description = description;
		this.runtimeMin = runtimeMin;
		this.ageLimit = ageLimit;
		this.posterUrl = posterUrl;
		this.noticeUrl = noticeUrl;
		this.startTime = startTime;
		this.endTime = endTime;
	}
}

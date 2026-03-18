package com.truve.platform.musical.show.domain.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.show.domain.converter.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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

	@Column(nullable = false)
	private Long venueId;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	private Integer runtimeMin;

	private Integer ageLimit;

	@Column(nullable = false, length = 500)
	private String posterImg;

	@Convert(converter = StringListConverter.class)
	@Column(name = "notice_img", length = 2000)
	private List<String> noticeImg;

	@Convert(converter = StringListConverter.class)
	@Column(name = "detail_img", length = 2000)
	private List<String> detailImg;

	@Column(name = "start_time")
	private LocalDateTime startTime;

	@Column(name = "end_time")
	private LocalDateTime endTime;

	@Builder
	private Show(
		Long venueId,
		String title,
		String description,
		Integer runtimeMin,
		Integer ageLimit,
		String posterImg,
		List<String> noticeImg,
		List<String> detailImg,
		LocalDateTime startTime,
		LocalDateTime endTime
	) {
		this.venueId = venueId;
		this.title = title;
		this.description = description;
		this.runtimeMin = runtimeMin;
		this.ageLimit = ageLimit;
		this.posterImg = posterImg;
		this.noticeImg = noticeImg;
		this.detailImg = detailImg;
		this.startTime = startTime;
		this.endTime = endTime;
	}
}

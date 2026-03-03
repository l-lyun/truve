package com.truve.platform.show.service.domain.entity;

import java.time.LocalDateTime;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.show.service.domain.constant.ShowScheduleStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "show_schedule")
@AttributeOverride(name = "id", column = @Column(name = "show_schedule_id"))
public class ShowSchedule extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@Column(nullable = false)
	private LocalDateTime showTime;

	@Column(nullable = false, length = 30)
	@Enumerated(EnumType.STRING)
	private ShowScheduleStatus status;

	private LocalDateTime date;

	@Builder
	private ShowSchedule(
		Show show,
		LocalDateTime showTime,
		ShowScheduleStatus status,
		LocalDateTime date
	) {
		this.show = show;
		this.showTime = showTime;
		this.status = status;
		this.date = date;
	}
}

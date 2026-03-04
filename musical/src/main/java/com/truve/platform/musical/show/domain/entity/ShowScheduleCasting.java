package com.truve.platform.musical.show.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "show_schedule_casting")
public class ShowScheduleCasting extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "show_cast_id", nullable = false)
	private ShowCasting showCasting;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "show_schedule_id", nullable = false)
	private ShowSchedule showSchedule;
}

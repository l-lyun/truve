package com.truve.platform.musical.service.domain.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "musical_schedules")
public class MusicalSchedule extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "musical_id", nullable = false)
	private Musical musical;

	@Column(nullable = false)
	private LocalDateTime dateTime;

	@Column(nullable = false)
	private Boolean isAvailable;

	@OneToMany(mappedBy = "schedule")
	private List<MusicalActor> actors = new ArrayList<>();

	@Builder
	private MusicalSchedule(Musical musical, LocalDateTime dateTime, Boolean isAvailable) {
		this.musical = musical;
		this.dateTime = dateTime;
		this.isAvailable = isAvailable;
	}
}

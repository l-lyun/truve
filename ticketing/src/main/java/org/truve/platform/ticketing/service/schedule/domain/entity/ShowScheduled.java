package org.truve.platform.ticketing.service.schedule.domain.entity;

import java.time.LocalDateTime;

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
@Table(name = "show_scheduled")
public class ShowScheduled extends BaseEntity {


	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String venue_name;

	@Column(nullable = false)
	LocalDateTime startAt;

	@Builder
	public ShowScheduled(String title, String venue_name, LocalDateTime startAt) {

		this.title = title;
		this.venue_name = venue_name;
		this.startAt = startAt;
	}
}

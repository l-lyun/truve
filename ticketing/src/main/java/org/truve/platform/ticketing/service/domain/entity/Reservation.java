package org.truve.platform.ticketing.service.domain.entity;

import java.time.LocalDateTime;

import org.truve.platform.ticketing.service.constant.ReservationStatus;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservations")
public class Reservation extends BaseEntity {

	@Column(nullable = false)
	private Long showScheduleId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ReservationStatus status;

	@Column
	private Long totalAmount;

	@Column
	private LocalDateTime paidAt;

	@Column(nullable = false)
	private Long userId;

	@Builder
	public Reservation(Long showScheduleId, Long totalAmount, Long userId) {
		this.showScheduleId = showScheduleId;
		this.status = ReservationStatus.CREATED;
		this.totalAmount = totalAmount;
		this.userId = userId;
	}

}

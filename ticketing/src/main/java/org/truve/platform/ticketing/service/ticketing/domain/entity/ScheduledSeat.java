package org.truve.platform.ticketing.service.ticketing.domain.entity;

import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "scheduled_seat")
public class ScheduledSeat extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seat_id")
	private Seat seat;

	@Column(nullable = false)
	private Long showScheduleId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private SeatStatus status;


	@Builder
	public ScheduledSeat(
		Seat seat,
		Long showScheduleId
		) {
		this.showScheduleId = showScheduleId;
		this.seat = seat;
		this.status = SeatStatus.AVAILABLE;
	}

	public boolean  isAvailable() {
		return status == SeatStatus.AVAILABLE;
	}

	public void cancelSeat() {
		this.status = SeatStatus.AVAILABLE;
	}

	public void purchaseSeat() {
		if (this.status == SeatStatus.SOLD) {
			throw new CustomException(ErrorCode.ALREADY_SOLD_SEAT);
		}
		this.status = SeatStatus.SOLD;
	}

}

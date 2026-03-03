package org.truve.platform.ticketing.service.domain.entity;

import org.truve.platform.ticketing.service.constant.SeatStatus;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
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
@Table(name = "show_schedule_seat_mapping")
public class ShowScheduleSeat extends BaseEntity {

	@Column(nullable = false)
	private Long showScheduleId;

	@Column(nullable = false)
	private Long seatId;

	@Column(nullable = false)
	private Long showSeatGradeId;

	@Column(nullable = false)
	private Long priceSnapshot;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private SeatStatus status;

	@Builder
	public ShowScheduleSeat(
		Long showScheduleId,
		Long seatId,
		Long showSeatGradeId,
		Long priceSnapshot
	) {
		this.showScheduleId = showScheduleId;
		this.seatId = seatId;
		this.showSeatGradeId = showSeatGradeId;
		this.priceSnapshot = priceSnapshot;
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

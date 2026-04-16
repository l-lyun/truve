package org.truve.platform.ticketing.service.booking.domain.entity;

import java.time.LocalDateTime;

import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.common.support.Preconditions;

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
@Table(name = "tickets")
public class Ticket extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_id")
	private Reservation reservation;

	@Column(name = "ticket_number", unique = true, nullable = false)
	private String number;

	@Column(nullable = false)
	private Long priceSnapshot;

	@Column(nullable = false)
	private String grade;

	@Column(nullable = false)
	private String seatDetail;

	@Column(nullable = false)
	private Long scheduledSeatId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TicketStatus status;

	@Column
	private LocalDateTime canceledAt;

	@Column
	private LocalDateTime usedAt;

	@Builder
	private Ticket(
		Reservation reservation,
		String number,
		String grade,
		Long priceSnapshot,
		String seatDetail,
		Long scheduledSeatId
	) {
		this.reservation = reservation;
		this.number = number;
		this.grade = grade;
		this.priceSnapshot = priceSnapshot;
		this.seatDetail = seatDetail;
		this.scheduledSeatId = scheduledSeatId;
		this.status = TicketStatus.ISSUED;
	}

	public static Ticket create(
		Reservation reservation,
		String number,
		String grade,
		Long priceSnapshot,
		String seatDetail,
		Long scheduledSeatId
	) {
		return Ticket.builder()
			.reservation(reservation)
			.number(number)
			.grade(grade)
			.priceSnapshot(priceSnapshot)
			.seatDetail(seatDetail)
			.scheduledSeatId(scheduledSeatId)
			.build();
	}

	public boolean isCanceled() {
		return status == TicketStatus.CANCELED;
	}

	public void cancel(LocalDateTime canceledAt) {
		Preconditions.validate(!isCanceled(), ErrorCode.ALREADY_CANCELED_TICKET);

		this.status = TicketStatus.CANCELED;
		this.canceledAt = canceledAt;
	}
}

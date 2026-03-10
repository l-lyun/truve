package org.truve.platform.ticketing.service.booking.domain.entity;

import java.time.LocalDateTime;

import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;

import com.truve.platform.common.support.BaseEntity;

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
	private String seatDetail;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TicketStatus status;

	@Column
	private LocalDateTime usedAt;

	@Builder
	private Ticket(Reservation reservation, String number, Long priceSnapshot, String seatDetail) {
		this.reservation = reservation;
		this.number = number;
		this.priceSnapshot = priceSnapshot;
		this.seatDetail = seatDetail;
		this.status = TicketStatus.ISSUED;
	}

	public static Ticket create(Reservation reservation, String number, Long priceSnapshot, String seatDetail) {
		return Ticket.builder()
			.reservation(reservation)
			.number(number)
			.priceSnapshot(priceSnapshot)
			.seatDetail(seatDetail)
			.build();
	}
}

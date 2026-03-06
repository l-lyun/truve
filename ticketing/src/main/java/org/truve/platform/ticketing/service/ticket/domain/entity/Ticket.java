package org.truve.platform.ticketing.service.ticket.domain.entity;

import java.time.LocalDateTime;

import org.truve.platform.ticketing.service.ticket.domain.constant.TicketStatus;

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
@Table(name = "tickets")
public class Ticket extends BaseEntity {

	@Column(nullable = false)
	private Long reservationId;

	@Column(nullable = false)
	private Long scheduleSeatMappingId;

	@Column(nullable = false)
	private String ticketNumber;

	@Column(nullable = false)
	private Long priceSnapshot;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TicketStatus status;

	@Column(nullable = false)
	private LocalDateTime issuedAt;

	@Column
	private LocalDateTime usedAt;

	@Builder
	public Ticket(Long reservationId, Long scheduleSeatMappingId, String ticketNumber, Long priceSnapshot) {
		this.reservationId = reservationId;
		this.scheduleSeatMappingId = scheduleSeatMappingId;
		this.ticketNumber = ticketNumber;
		this.priceSnapshot = priceSnapshot;
		this.status = TicketStatus.ISSUED;
		this.issuedAt = LocalDateTime.now();
	}

}

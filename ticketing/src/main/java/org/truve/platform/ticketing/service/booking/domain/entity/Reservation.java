package org.truve.platform.ticketing.service.booking.domain.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
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
	private UUID userId;

	@Column(name = "reservation_number", unique = true, nullable = false)
	private String number;

	@Column(nullable = false)
	private Long totalAmount;

	@Column(nullable = false)
	private String gradeSummary;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ReservationStatus status;

	@Column
	private LocalDateTime paidAt;

	@Embedded
	private Applicant applicant;

	@OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
	private List<Ticket> tickets = new ArrayList<>();

	@Builder
	private Reservation(UUID userId, String number, Long totalAmount, String gradeSummary) {

		this.userId = userId;
		this.number = number;
		this.totalAmount = totalAmount;
		this.gradeSummary = gradeSummary;
		this.status = ReservationStatus.CREATED;
	}

	public static Reservation create(UUID userId, String number, Long totalAmount, String gradeSummary) {
		return Reservation.builder()
			.userId(userId)
			.number(number)
			.totalAmount(totalAmount)
			.gradeSummary(gradeSummary)
			.build();
	}

	public void addTickets(List<Ticket> tickets) {
		this.tickets.addAll(tickets);
	}

	public void readyForPayment(Applicant applicant) {
		this.applicant = applicant;
		this.status = ReservationStatus.PENDING_PAYMENT;
	}

	public void confirm(LocalDateTime paidAt, boolean isDepositPending) {
		this.paidAt = paidAt;
		this.status = isDepositPending ? ReservationStatus.PENDING_DEPOSIT : ReservationStatus.CONFIRMED;
	}

	public void depositReceive(LocalDateTime paidAt) {
		this.paidAt = paidAt;
		this.status = ReservationStatus.CONFIRMED;
	}
}

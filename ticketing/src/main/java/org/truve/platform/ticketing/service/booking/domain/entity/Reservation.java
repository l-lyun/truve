package org.truve.platform.ticketing.service.booking.domain.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.policy.CancellationPolicy;

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
	private static final Long TICKET_SERVICE_FEE = 2000L;

	@Column(nullable = false)
	private UUID userId;

	@Column(name = "reservation_number", unique = true, nullable = false)
	private String number;

	@Column(nullable = false)
	private Long totalAmount;

	@Column(nullable = false)
	private Long serviceFee;

	@Column(nullable = false)
	private Long cancelFee;

	@Column(nullable = false)
	private String gradeSummary;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ReservationStatus status;

	@Column
	private LocalDateTime bookedAt;

	@Column
	private LocalDateTime paidAt;

	@Column
	private LocalDateTime canceledAt;

	@Embedded
	private VirtualAccount virtualAccount;

	@Column
	private String paymentMethod;

	@Embedded
	private ShowInfo showInfo;

	@Embedded
	private Applicant applicant;

	@OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
	private List<Ticket> tickets = new ArrayList<>();

	@Builder
	private Reservation(UUID userId, String number, String gradeSummary,
		ShowInfo showInfo) {

		this.userId = userId;
		this.number = number;
		this.totalAmount = 0L;
		this.serviceFee = 0L;
		this.cancelFee = 0L;
		this.gradeSummary = gradeSummary;
		this.showInfo = showInfo;
		this.status = ReservationStatus.CREATED;
	}

	public static Reservation create(
		UUID userId,
		String number,
		String gradeSummary,
		ShowInfo showInfo
	) {
		return Reservation.builder()
			.userId(userId)
			.number(number)
			.gradeSummary(gradeSummary)
			.showInfo(showInfo)
			.build();
	}

	public void addTickets(List<Ticket> tickets) {
		this.tickets.addAll(tickets);
		this.serviceFee = tickets.size() * TICKET_SERVICE_FEE;
		this.totalAmount = calculateTicketAmount() + this.serviceFee;
	}

	public Long calculateTicketAmount() {
		return tickets.stream().mapToLong(Ticket::getPriceSnapshot).sum();
	}

	public void readyForPayment(Applicant applicant) {
		this.applicant = applicant;
		this.status = ReservationStatus.PENDING_PAYMENT;
	}

	// TODO: 메서드 분리
	public void confirm(LocalDateTime bookedAt, LocalDateTime paidAt, String paymentMethod,
		VirtualAccount virtualAccount) {
		this.bookedAt = bookedAt;
		this.paidAt = paidAt;
		this.paymentMethod = paymentMethod;

		if (isVirtualAccountPayment(virtualAccount)) {
			this.virtualAccount = virtualAccount;
			this.status = ReservationStatus.PENDING_DEPOSIT;
		} else {
			this.status = ReservationStatus.CONFIRMED;
		}
	}

	private boolean isVirtualAccountPayment(VirtualAccount virtualAccount) {
		return virtualAccount != null;
	}

	public void depositReceive(LocalDateTime paidAt) {
		this.paidAt = paidAt;
		this.status = ReservationStatus.CONFIRMED;
	}

	public boolean isCancelable() {
		return status == ReservationStatus.CONFIRMED;
	}

	public boolean isCanceled() {
		return status == ReservationStatus.CANCELED || status == ReservationStatus.PARTIAL_CANCELED;
	}

	public boolean isReviewable() {
		return status == ReservationStatus.COMPLETED;
	}

	public boolean isWaitingDeposit() {
		return status == ReservationStatus.PENDING_DEPOSIT;
	}

	public List<Long> getTicketPrices() {
		return tickets.stream().map(Ticket::getPriceSnapshot).toList();
	}

	public Map<String, List<Ticket>> getTicketsGroupedByGrade() {
		return Collections.unmodifiableMap(
			tickets.stream().collect(Collectors.groupingBy(Ticket::getGrade))
		);
	}

	public List<Ticket> getCancelTickets() {
		return tickets.stream().filter(Ticket::isCanceled).toList();
	}

	public List<String> getCanceledSeatDetails() {
		return getCancelTickets().stream().map(Ticket::getSeatDetail).toList();
	}

	public Long getRefundAmount() {
		Long canceledTicketPrice = getCancelTickets().stream().mapToLong(Ticket::getPriceSnapshot).sum();
		return canceledTicketPrice - cancelFee;
	}

	public LocalDateTime getDeadline() {
		if (isCancelable())
			return showInfo.getStartAt();
		if (isWaitingDeposit())
			return virtualAccount.getDueDate();
		return null;
	}

	public Long calculateCancelFee(LocalDateTime canceledAt, List<Long> ticketIds) {
		return CancellationPolicy.calculate(this, getTicketsByIds(ticketIds), canceledAt);
	}

	public Long calculateRefundAmount(LocalDateTime canceledAt, List<Long> ticketIds) {
		boolean isBookedDay = bookedAt.toLocalDate().isEqual(canceledAt.toLocalDate());
		return (isBookedDay ? getTicketTotalAmount(ticketIds) : getTicketAmount(ticketIds))
			- calculateCancelFee(canceledAt, ticketIds);
	}

	private List<Ticket> getTicketsByIds(List<Long> ticketIds) {
		return tickets.stream().filter(t -> ticketIds.contains(t.getId())).toList();
	}

	private Long getTicketAmount(List<Long> ticketIds) {
		return getTicketsByIds(ticketIds).stream().mapToLong(Ticket::getPriceSnapshot).sum();
	}

	public Long getTicketTotalAmount(List<Long> ticketIds) {
		return getTicketAmount(ticketIds) + TICKET_SERVICE_FEE * ticketIds.size();
	}
}

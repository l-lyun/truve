package org.truve.platform.ticketing.service.booking.domain.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.Applicant;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.VirtualAccount;
import org.truve.platform.ticketing.service.booking.domain.policy.CancellationPolicy;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.common.support.Preconditions;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "reservations",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_reservation_user_schedule_block_booking",
		columnNames = {"user_id", "show_schedule_id", "block_booking"}
	)
)
public class Reservation extends BaseEntity {
	private static final Long TICKET_SERVICE_FEE = 2000L;

	@Column(nullable = false)
	private UUID userId;

	@Column(name = "reservation_number", unique = true, nullable = false)
	private String number;

	@Column(name = "hold_id", unique = true)
	private String holdId;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

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

	@Version
	private Long version;

	@Column(name = "block_booking")
	private Boolean blockBooking;

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

	@Builder(access = AccessLevel.PRIVATE)
	private Reservation(UUID userId, String number, String gradeSummary,
		ShowInfo showInfo, String holdId, LocalDateTime expiresAt, ReservationStatus status) {

		this.userId = userId;
		this.number = number;
		this.totalAmount = 0L;
		this.serviceFee = 0L;
		this.cancelFee = 0L;
		this.gradeSummary = gradeSummary;
		this.showInfo = showInfo;
		this.holdId = holdId;
		this.expiresAt = expiresAt;
		this.status = status;
		this.blockBooking = true;
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
			.status(ReservationStatus.CREATED)
			.build();
	}

	public static Reservation createHoldPending(
		UUID userId,
		String number,
		String gradeSummary,
		ShowInfo showInfo,
		String holdId,
		LocalDateTime expiresAt
	) {
		Preconditions.validate(
			holdId != null && !holdId.isBlank() && expiresAt != null,
			ErrorCode.INVALID_BOOKING_SEAT_HOLD
		);
		return Reservation.builder()
			.userId(userId)
			.number(number)
			.gradeSummary(gradeSummary)
			.showInfo(showInfo)
			.holdId(holdId)
			.expiresAt(expiresAt)
			.status(ReservationStatus.HOLD_PENDING)
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
		Preconditions.validate(
			status == ReservationStatus.CREATED || status == ReservationStatus.PAYMENT_READY,
			ErrorCode.INVALID_RESERVATION_STATUS
		);
		this.applicant = applicant;
		this.status = ReservationStatus.PENDING_PAYMENT;
	}

	public PaymentTransitionResult confirm(LocalDateTime bookedAt, LocalDateTime paidAt, String paymentMethod,
		VirtualAccount virtualAccount) {
		if (status == ReservationStatus.CONFIRMED) {
			validateAllTicketsIssued();
			return PaymentTransitionResult.ALREADY_APPLIED;
		}
		if (status == ReservationStatus.PENDING_DEPOSIT && isVirtualAccountPayment(virtualAccount)) {
			validateAllTicketsPending();
			return PaymentTransitionResult.ALREADY_APPLIED;
		}
		if (isTerminalPaymentState()) {
			return PaymentTransitionResult.TERMINAL_IGNORED;
		}
		Preconditions.validate(status == ReservationStatus.PENDING_PAYMENT, ErrorCode.INVALID_RESERVATION_STATUS);

		this.bookedAt = bookedAt;
		this.paidAt = paidAt;
		this.paymentMethod = paymentMethod;

		if (isVirtualAccountPayment(virtualAccount)) {
			this.virtualAccount = virtualAccount;
			this.status = ReservationStatus.PENDING_DEPOSIT;
			return PaymentTransitionResult.PENDING_DEPOSIT;
		} else {
			this.status = ReservationStatus.CONFIRMED;
			issueTickets();
			return PaymentTransitionResult.CONFIRMED;
		}
	}

	private boolean isVirtualAccountPayment(VirtualAccount virtualAccount) {
		return virtualAccount != null;
	}

	public PaymentTransitionResult depositReceive(LocalDateTime paidAt) {
		if (status == ReservationStatus.CONFIRMED) {
			validateAllTicketsIssued();
			return PaymentTransitionResult.ALREADY_APPLIED;
		}
		if (isTerminalPaymentState()) {
			return PaymentTransitionResult.TERMINAL_IGNORED;
		}
		Preconditions.validate(status == ReservationStatus.PENDING_DEPOSIT, ErrorCode.INVALID_RESERVATION_STATUS);

		this.paidAt = paidAt;
		this.status = ReservationStatus.CONFIRMED;
		issueTickets();
		return PaymentTransitionResult.CONFIRMED;
	}

	private void issueTickets() {
		tickets.forEach(Ticket::issue);
	}

	private boolean isTerminalPaymentState() {
		return status == ReservationStatus.CANCELED
			|| status == ReservationStatus.PARTIAL_CANCELED
			|| status == ReservationStatus.COMPLETED;
	}

	private void validateAllTicketsIssued() {
		Preconditions.validate(
			tickets.stream().allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED),
			ErrorCode.INVALID_TICKET_STATUS
		);
	}

	private void validateAllTicketsPending() {
		Preconditions.validate(
			tickets.stream().allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING),
			ErrorCode.INVALID_TICKET_STATUS
		);
	}

	public enum PaymentTransitionResult {
		CONFIRMED,
		PENDING_DEPOSIT,
		ALREADY_APPLIED,
		TERMINAL_IGNORED
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

	public void cancel(List<Long> ticketIds, LocalDateTime canceledAt) {
		this.canceledAt = canceledAt;

		tickets.stream()
			.filter(ticket -> ticketIds.contains(ticket.getId()))
			.forEach(ticket -> ticket.cancel(canceledAt));

		boolean allTicketsCanceled = tickets.stream().allMatch(Ticket::isCanceled);
		this.status = allTicketsCanceled ? ReservationStatus.CANCELED : ReservationStatus.PARTIAL_CANCELED;
		if (allTicketsCanceled) {
			this.blockBooking = null;
		}
	}

	public void validateTicketId(List<Long> ticketIds) {
		Preconditions.validate(
			ticketIds != null
				&& !ticketIds.isEmpty()
				&& ticketIds.stream().noneMatch(Objects::isNull)
				&& new HashSet<>(ticketIds).size() == ticketIds.size(),
			ErrorCode.INVALID_TICKET_ID
		);
		Set<Long> validIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
		Preconditions.validate(validIds.containsAll(ticketIds), ErrorCode.INVALID_TICKET_ID);
	}

	public void validateCancelableTicketIds(List<Long> ticketIds) {
		validateTicketId(ticketIds);
		boolean allCancelable = tickets.stream()
			.filter(ticket -> ticketIds.contains(ticket.getId()))
			.noneMatch(Ticket::isCanceled);
		Preconditions.validate(allCancelable, ErrorCode.ALREADY_CANCELED_TICKET);
	}

	public void validateCancelStatus() {
		Preconditions.validate(status != ReservationStatus.CANCELED && status != ReservationStatus.COMPLETED,
			ErrorCode.ALREADY_CANCELED_OR_COMPLETED);
	}
}

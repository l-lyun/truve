package org.truve.platform.ticketing.service.ticketing.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.service.PaymentCreationOutboxPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoldRequestedTransactionService {
	private static final String SEAT_DETAIL_FORMAT = "%d층 %s구역 %s열 %d번";

	private final ReservationRepository reservationRepository;
	private final ScheduledSeatRepository scheduledSeatRepository;
	private final PaymentCreationOutboxPublisher paymentCreationOutboxPublisher;
	private final Clock clock;

	@Transactional
	public ApplyResult apply(TicketingEventCommand.HoldRequested event) {
		Reservation reservation = reservationRepository.findByHoldIdWithTickets(event.getHoldId())
			.orElseThrow(() -> new IllegalStateException("HOLD 주문을 찾을 수 없습니다. holdId=" + event.getHoldId()));
		validateEvent(reservation, event);

		if (reservation.getStatus() == ReservationStatus.PAYMENT_READY) {
			validateAppliedState(reservation, event);
			return ApplyResult.ALREADY_APPLIED;
		}
		if (reservation.getStatus() == ReservationStatus.HOLD_FAILED
			|| reservation.getStatus() == ReservationStatus.EXPIRED) {
			return ApplyResult.TERMINAL_IGNORED;
		}
		Preconditions.validate(
			reservation.getStatus() == ReservationStatus.HOLD_PENDING,
			ErrorCode.INVALID_RESERVATION_STATUS
		);
		if (!LocalDateTime.now(clock).isBefore(event.getExpiresAt())) {
			throw HoldRequestedApplyException.expired();
		}

		List<Long> sortedSeatIds = event.getScheduledSeatIds().stream().sorted().toList();
		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllByIdWithSeat(sortedSeatIds);
		validateSeats(event, scheduledSeats, sortedSeatIds);

		List<Ticket> tickets = scheduledSeats.stream()
			.map(scheduledSeat -> createTicket(scheduledSeat, reservation))
			.toList();
		reservation.addTickets(tickets);
		LocalDateTime reservedAt = LocalDateTime.now(clock);
		scheduledSeats.forEach(seat -> seat.reserve(reservation.getNumber(), reservedAt));
		reservation.completeHold();
		paymentCreationOutboxPublisher.publish(PaymentEventCommand.Create.of(reservation));

		// 좌석과 Reservation의 @Version 충돌을 Consumer 반환 전에 확인한다.
		reservationRepository.flush();
		return ApplyResult.APPLIED;
	}

	@Transactional(readOnly = true)
	public RecoveryResult resolveAfterFailure(TicketingEventCommand.HoldRequested event) {
		return reservationRepository.findByHoldIdWithTickets(event.getHoldId())
			.map(reservation -> {
				validateEvent(reservation, event);
				if (reservation.getStatus() == ReservationStatus.PAYMENT_READY) {
					validateAppliedState(reservation, event);
					return RecoveryResult.APPLIED;
				}
				if (reservation.getStatus() == ReservationStatus.HOLD_FAILED
					|| reservation.getStatus() == ReservationStatus.EXPIRED) {
					return RecoveryResult.TERMINAL;
				}
				if (reservation.getStatus() != ReservationStatus.HOLD_PENDING) {
					return RecoveryResult.RETRY_REQUIRED;
				}

				List<Long> eventSeatIds = event.getScheduledSeatIds().stream().sorted().toList();
				List<ScheduledSeat> seats = scheduledSeatRepository.findAllById(eventSeatIds);
				if (seats.size() != eventSeatIds.size()
					|| seats.stream().anyMatch(seat ->
					!Objects.equals(seat.getShowScheduleId(), event.getShowScheduleId()))) {
					return RecoveryResult.CONFLICT;
				}
				if (seats.stream().allMatch(ScheduledSeat::isAvailable)) {
					return RecoveryResult.RETRY_REQUIRED;
				}
				if (seats.stream().anyMatch(seat ->
					seat.getStatus() != SeatStatus.HOLD
						|| !Objects.equals(seat.getReservationNumber(), reservation.getNumber()))) {
					return RecoveryResult.CONFLICT;
				}
				return RecoveryResult.RETRY_REQUIRED;
			})
			.orElse(RecoveryResult.RETRY_REQUIRED);
	}

	private void validateEvent(Reservation reservation, TicketingEventCommand.HoldRequested event) {
		Preconditions.validate(
			Objects.equals(reservation.getNumber(), event.getReservationNumber())
				&& Objects.equals(reservation.getUserId(), event.getUserId())
				&& reservation.getShowInfo() != null
				&& Objects.equals(reservation.getShowInfo().getShowScheduleId(), event.getShowScheduleId())
				&& Objects.equals(
					reservation.getHoldRequestFingerprint(),
					NumberGenerator.generateHoldRequestFingerprint(event.getScheduledSeatIds())
				)
				&& sameExpiry(reservation.getExpiresAt(), event.getExpiresAt()),
			ErrorCode.INVALID_BOOKING_SEAT_HOLD
		);
	}

	private boolean sameExpiry(LocalDateTime stored, LocalDateTime received) {
		return stored != null && received != null
			&& stored.truncatedTo(ChronoUnit.MILLIS).equals(received.truncatedTo(ChronoUnit.MILLIS));
	}

	private void validateSeats(
		TicketingEventCommand.HoldRequested event,
		List<ScheduledSeat> scheduledSeats,
		List<Long> sortedSeatIds
	) {
		if (scheduledSeats.size() != sortedSeatIds.size()
			|| scheduledSeats.stream().anyMatch(seat -> !event.getShowScheduleId().equals(seat.getShowScheduleId()))) {
			throw HoldRequestedApplyException.seatConflict();
		}
		if (scheduledSeats.stream().anyMatch(seat -> !seat.isAvailable())) {
			throw HoldRequestedApplyException.seatConflict();
		}
	}

	private void validateAppliedState(Reservation reservation, TicketingEventCommand.HoldRequested event) {
		List<Long> ticketSeatIds = reservation.getTickets().stream()
			.map(Ticket::getScheduledSeatId)
			.sorted()
			.toList();
		List<Long> eventSeatIds = event.getScheduledSeatIds().stream().sorted().toList();
		Preconditions.validate(ticketSeatIds.equals(eventSeatIds), ErrorCode.INVALID_BOOKING_SEAT_HOLD);

		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllById(eventSeatIds);
		Preconditions.validate(
			scheduledSeats.size() == eventSeatIds.size()
				&& scheduledSeats.stream().allMatch(seat ->
				seat.getStatus() == SeatStatus.HOLD
					&& Objects.equals(seat.getReservationNumber(), reservation.getNumber())),
			ErrorCode.INVALID_BOOKING_SEAT_HOLD
		);
	}

	private Ticket createTicket(ScheduledSeat scheduledSeat, Reservation reservation) {
		Seat seat = scheduledSeat.getSeat();
		SeatSection section = seat.getSeatSection();
		return Ticket.create(
			reservation,
			NumberGenerator.generateTicketNumber(),
			section.getGradeName(),
			section.getPrice(),
			SEAT_DETAIL_FORMAT.formatted(
				section.getFloor(), section.getName(), seat.getSeatRow(), seat.getSeatNumber()),
			scheduledSeat.getId()
		);
	}

	public enum ApplyResult {
		APPLIED,
		ALREADY_APPLIED,
		TERMINAL_IGNORED
	}

	public enum RecoveryResult {
		APPLIED,
		TERMINAL,
		CONFLICT,
		RETRY_REQUIRED
	}
}

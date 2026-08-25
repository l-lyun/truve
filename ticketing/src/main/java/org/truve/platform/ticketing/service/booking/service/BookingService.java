package org.truve.platform.ticketing.service.booking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.VirtualAccount;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentClient;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentRequest;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingPublisher;
import org.truve.platform.ticketing.service.booking.risk.service.BookingBotRiskService;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;
import org.truve.platform.ticketing.service.ticketing.service.SeatHoldService;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {
	private final ReservationRepository reservationRepository;
	private final BookingCreationService bookingCreationService;
	private final BookingLockService bookingLockService;
	private final SeatHoldService seatHoldService;
	private final PaymentPublisher paymentPublisher;
	private final PaymentClient paymentClient;
	private final TicketingPublisher ticketingPublisher;
	private final BookingBotRiskService bookingBotRiskService;

	public BookingResponse.Create create(UUID userId, String sessionToken, BookingRequest.Create request) {
		Preconditions.validate(request != null, ErrorCode.NOT_CORRECT_SEAT);
		validateCreateRequest(request);
		Long showScheduleId = request.getShowScheduleId();
		List<Long> scheduledSeatIds = request.getScheduledSeatIds();

		seatHoldService.validateSession(userId, showScheduleId, sessionToken);
		BookingLockService.BookingLock bookingLock = bookingLockService.acquire(userId, showScheduleId);

		try {
			Preconditions.validate(
				!reservationRepository.existsBlockingBooking(userId, showScheduleId),
				ErrorCode.ALREADY_BOOKED_SHOW
			);

			String reservationNumber = NumberGenerator.generateReservationNumber();
			SeatHoldService.SeatClaim claim = seatHoldService.claim(
				showScheduleId,
				scheduledSeatIds,
				sessionToken,
				reservationNumber
			);

			try {
				BookingResponse.Create response = bookingCreationService.create(
					userId,
					showScheduleId,
					scheduledSeatIds,
					reservationNumber
				);
				releaseSeatClaim(claim);
				return response;
			} catch (DataIntegrityViolationException exception) {
				restoreSeatClaim(claim);
				throw new CustomException(ErrorCode.ALREADY_BOOKED_SHOW);
			} catch (RuntimeException exception) {
				if (reservationRepository.existsByNumber(reservationNumber)) {
					releaseSeatClaim(claim);
					return new BookingResponse.Create(reservationNumber);
				}
				restoreSeatClaim(claim);
				throw exception;
			}
		} finally {
			releaseBookingLock(bookingLock);
		}
	}

	private void validateCreateRequest(BookingRequest.Create request) {
		Preconditions.validate(request.getShowScheduleId() != null, ErrorCode.INVALID_SHOW_SCHEDULE);
		List<Long> scheduledSeatIds = request.getScheduledSeatIds();
		Preconditions.validate(scheduledSeatIds != null && !scheduledSeatIds.isEmpty(), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(scheduledSeatIds.size() <= 4, ErrorCode.EXCEEDED_MAX_TICKET_COUNT);
		Preconditions.validate(scheduledSeatIds.stream().noneMatch(Objects::isNull), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(
			new HashSet<>(scheduledSeatIds).size() == scheduledSeatIds.size(),
			ErrorCode.NOT_CORRECT_SEAT
		);
	}

	private void releaseSeatClaim(SeatHoldService.SeatClaim claim) {
		try {
			seatHoldService.release(claim);
		} catch (RuntimeException exception) {
			log.warn("좌석 claim 정리에 실패했습니다. reservation claim={}", claim.claimValue(), exception);
		}
	}

	private void restoreSeatClaim(SeatHoldService.SeatClaim claim) {
		try {
			boolean restored = seatHoldService.restore(claim);
			if (!restored) {
				log.warn("복원할 좌석 claim이 남아 있지 않습니다. reservation claim={}", claim.claimValue());
			}
		} catch (RuntimeException restoreException) {
			log.warn("좌석 claim 복원에 실패했습니다. reservation claim={}", claim.claimValue(), restoreException);
		}
	}

	private void releaseBookingLock(BookingLockService.BookingLock bookingLock) {
		try {
			bookingLockService.release(bookingLock);
		} catch (RuntimeException exception) {
			log.warn("예매 생성 락 해제에 실패했습니다. lockToken={}", bookingLock.lockToken(), exception);
		}
	}

	@Transactional(readOnly = true)
	public BookingResponse.Order getOrder(String reservationNumber) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);
		return BookingResponse.Order.from(reservation);
	}

	@Transactional(readOnly = true)
	public BookingResponse.ReservationDetail getDetail(String reservationNumber) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);
		return BookingResponse.ReservationDetail.from(reservation);
	}

	@Transactional(readOnly = true)
	public List<BookingResponse.Summary> getSummaries(UUID userId, LocalDate from, LocalDate to) {
		LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
		LocalDateTime toDt = to == null ? null : to.atTime(LocalTime.MAX);

		List<Reservation> reservations = reservationRepository
			.findByUserIdAndDateRange(userId, fromDt, toDt);
		return reservations.stream().map(BookingResponse.Summary::from).toList();
	}

	@Transactional
	public void paymentReady(String reservationNumber, BookingRequest.ApplicantInfo request) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);
		bookingBotRiskService.validatePaymentReady(reservation.getUserId());

		reservation.readyForPayment(request.toEntity());

		paymentPublisher.publish(PaymentEventCommand.Create.of(reservation));
	}

	@Transactional
	public void confirm(BookingEventCommand.Confirmed event) {
		Reservation reservation = reservationRepository.findByNumber(event.getReservationNumber());
		reservation.confirm(
			event.getBookedAt(),
			event.getPaidAt(),
			event.getMethod(),
			VirtualAccount.from(event.getVirtualAccount())
		);

		List<Long> scheduledSeatIds = reservation.getTickets().stream()
			.map(Ticket::getScheduledSeatId)
			.toList();
		ticketingPublisher.publish(TicketingEventCommand.SoldConfirmed.of(reservation, scheduledSeatIds));
	}

	@Transactional
	public void depositReceive(BookingEventCommand.DepositReceived event) {
		Reservation reservation = reservationRepository.findByNumber(event.getReservationNumber());
		reservation.depositReceive(event.getPaidAt());
	}

	@Transactional(readOnly = true)
	public BookingResponse.Cancel getCancel(String reservationNumber, List<Long> ticketIds) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);

		List<Long> resolvedTicketIds = ticketIds != null ? ticketIds
			: reservation.getTickets().stream().map(Ticket::getId).toList();

		reservation.validateTicketId(resolvedTicketIds);

		return BookingResponse.Cancel.from(reservation, resolvedTicketIds, LocalDateTime.now());
	}

	@Transactional
	public BookingResponse.CanceledTickets cancel(String reservationNumber, BookingRequest.Cancel request) {
		Reservation reservation = reservationRepository.findByNumberForUpdate(reservationNumber);

		List<Long> ticketIds = request.getTicketIds();
		reservation.validateCancelStatus();
		reservation.validateCancelableTicketIds(ticketIds);

		List<Long> scheduledSeatIds = reservation.getTickets().stream()
			.filter(ticket -> ticketIds.contains(ticket.getId()))
			.map(Ticket::getScheduledSeatId)
			.toList();
		LocalDateTime canceledAt = LocalDateTime.now();

		Long refundAmount = reservation.calculateRefundAmount(canceledAt, ticketIds);
		paymentClient.cancel(
			reservationNumber,
			NumberGenerator.generateIdempotencyKey(reservationNumber, ticketIds),
			PaymentRequest.Cancel.of(request.getCancelReason(), refundAmount)
		);

		reservation.cancel(ticketIds, canceledAt);
		ticketingPublisher.publish(TicketingEventCommand.HoldReleased.of(reservation, scheduledSeatIds));

		return new BookingResponse.CanceledTickets(ticketIds);
	}
}

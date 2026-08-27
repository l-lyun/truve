package org.truve.platform.ticketing.service.ticketing.service;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoldRequestedFailureService {
	private final ReservationRepository reservationRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public FailureRecordResult record(
		TicketingEventCommand.HoldRequested event,
		HoldRequestedApplyException.FailureReason reason
	) {
		Reservation reservation = reservationRepository.findByHoldId(event.getHoldId())
			.orElseThrow(() -> new IllegalStateException("HOLD 주문을 찾을 수 없습니다. holdId=" + event.getHoldId()));
		validateReservationIdentity(reservation, event);

		if (reservation.getStatus() == ReservationStatus.PAYMENT_READY) {
			return FailureRecordResult.COMPLETED_IGNORED;
		}
		if (reservation.getStatus() == ReservationStatus.HOLD_FAILED
			|| reservation.getStatus() == ReservationStatus.EXPIRED) {
			return FailureRecordResult.ALREADY_TERMINAL;
		}
		Preconditions.validate(
			reservation.getStatus() == ReservationStatus.HOLD_PENDING,
			ErrorCode.INVALID_RESERVATION_STATUS
		);

		if (reason == HoldRequestedApplyException.FailureReason.EXPIRED) {
			reservation.expireHold();
		} else {
			reservation.failHold();
		}
		reservationRepository.flush();
		return FailureRecordResult.RECORDED;
	}

	private void validateReservationIdentity(
		Reservation reservation,
		TicketingEventCommand.HoldRequested event
	) {
		Preconditions.validate(
			Objects.equals(reservation.getNumber(), event.getReservationNumber())
				&& Objects.equals(reservation.getUserId(), event.getUserId())
				&& reservation.getShowInfo() != null
				&& Objects.equals(reservation.getShowInfo().getShowScheduleId(), event.getShowScheduleId())
				&& Objects.equals(
					reservation.getHoldRequestFingerprint(),
					NumberGenerator.generateHoldRequestFingerprint(event.getScheduledSeatIds()))
				&& reservation.getExpiresAt() != null
				&& event.getExpiresAt() != null
				&& reservation.getExpiresAt().truncatedTo(ChronoUnit.MILLIS)
				.equals(event.getExpiresAt().truncatedTo(ChronoUnit.MILLIS)),
			ErrorCode.INVALID_BOOKING_SEAT_HOLD
		);
	}

	public enum FailureRecordResult {
		RECORDED,
		ALREADY_TERMINAL,
		COMPLETED_IGNORED
	}
}

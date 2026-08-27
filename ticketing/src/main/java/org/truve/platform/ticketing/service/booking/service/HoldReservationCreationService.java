package org.truve.platform.ticketing.service.booking.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.service.TicketingOutboxPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoldReservationCreationService {
	private static final String GRADE_SUMMARY_FORMAT = "%s석 %d인";
	private static final String LINE_BREAK = "\n";

	private final ReservationRepository reservationRepository;
	private final ScheduledSeatRepository scheduledSeatRepository;
	private final ShowScheduledRepository showScheduledRepository;
	private final TicketingOutboxPublisher outboxPublisher;

	@Transactional(readOnly = true)
	public Optional<HoldReservationResult> findExisting(
		String holdId,
		String holdRequestFingerprint,
		UUID userId,
		Long showScheduleId
	) {
		return reservationRepository.findByHoldId(holdId)
			.map(reservation -> toValidatedResult(
				reservation, holdRequestFingerprint, userId, showScheduleId));
	}

	@Transactional
	public HoldReservationResult create(HoldReservationCommand command) {
		Optional<Reservation> existing = reservationRepository.findByHoldId(command.holdId());
		if (existing.isPresent()) {
			return toValidatedResult(
				existing.get(), command.holdRequestFingerprint(), command.userId(), command.showScheduleId());
		}

		List<Long> sortedSeatIds = command.scheduledSeatIds().stream().sorted().toList();
		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllByIdWithSeat(sortedSeatIds);
		Preconditions.validate(scheduledSeats.size() == sortedSeatIds.size(), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(
			scheduledSeats.stream().allMatch(seat -> command.showScheduleId().equals(seat.getShowScheduleId())),
			ErrorCode.NOT_CORRECT_SEAT
		);
		Preconditions.validate(
			scheduledSeats.stream().allMatch(ScheduledSeat::isAvailable),
			ErrorCode.ALREADY_SOLD_SEAT
		);

		ShowScheduled showScheduled = showScheduledRepository.findById(command.showScheduleId())
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));
		String reservationNumber = NumberGenerator.generateReservationNumber();
		Reservation reservation = Reservation.createHoldPending(
			command.userId(),
			reservationNumber,
			createGradeSummary(scheduledSeats),
			createShowInfo(showScheduled),
			command.holdId(),
			command.holdRequestFingerprint(),
			command.expiresAt()
		);
		reservationRepository.save(reservation);
		outboxPublisher.publish(TicketingEventCommand.HoldRequested.of(
			command.holdId(),
			reservationNumber,
			command.userId(),
			command.sessionToken(),
			command.showScheduleId(),
			sortedSeatIds,
			command.expiresAt()
		));
		return HoldReservationResult.from(reservation);
	}

	private HoldReservationResult toValidatedResult(
		Reservation reservation,
		String holdRequestFingerprint,
		UUID userId,
		Long showScheduleId
	) {
		Preconditions.validate(reservation.getUserId().equals(userId), ErrorCode.INVALID_BOOKING_SEAT_HOLD);
		Preconditions.validate(
			reservation.getShowInfo().getShowScheduleId().equals(showScheduleId),
			ErrorCode.INVALID_BOOKING_SEAT_HOLD
		);
		Preconditions.validate(
			reservation.getHoldRequestFingerprint().equals(holdRequestFingerprint),
			ErrorCode.INVALID_BOOKING_SEAT_HOLD
		);
		return HoldReservationResult.from(reservation);
	}

	private String createGradeSummary(List<ScheduledSeat> scheduledSeats) {
		return scheduledSeats.stream()
			.collect(Collectors.groupingBy(
				seat -> seat.getSeat().getSeatSection().getGradeName(),
				LinkedHashMap::new,
				Collectors.counting()
			))
			.entrySet().stream()
			.map(entry -> GRADE_SUMMARY_FORMAT.formatted(entry.getKey(), entry.getValue()))
			.collect(Collectors.joining(LINE_BREAK));
	}

	private ShowInfo createShowInfo(ShowScheduled showScheduled) {
		return ShowInfo.builder()
			.showId(showScheduled.getShowId())
			.showScheduleId(showScheduled.getId())
			.title(showScheduled.getTitle())
			.venueName(showScheduled.getVenueName())
			.startAt(showScheduled.getStartAt())
			.posterImg(showScheduled.getPosterImg())
			.build();
	}

	public record HoldReservationCommand(
		String holdId,
		String holdRequestFingerprint,
		UUID userId,
		String sessionToken,
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		LocalDateTime expiresAt
	) {
		public HoldReservationCommand {
			scheduledSeatIds = List.copyOf(scheduledSeatIds);
		}
	}

	public record HoldReservationResult(
		String reservationNumber,
		ReservationStatus status,
		LocalDateTime expiresAt
	) {
		private static HoldReservationResult from(Reservation reservation) {
			return new HoldReservationResult(
				reservation.getNumber(), reservation.getStatus(), reservation.getExpiresAt());
		}
	}
}

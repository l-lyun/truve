package org.truve.platform.ticketing.service.booking.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingCreationService {
	private static final String GRADE_SUMMARY_FORMAT = "%s석 %d인";
	private static final String LINE_BREAK = "\n";
	private static final String SEAT_DETAIL_FORMAT = "%d층 %s구역 %s열 %d번";

	private final ReservationRepository reservationRepository;
	private final ScheduledSeatRepository scheduledSeatRepository;
	private final ShowScheduledRepository showScheduledRepository;

	@Transactional
	public BookingResponse.Create create(
		UUID userId,
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String reservationNumber
	) {
		List<Long> sortedSeatIds = scheduledSeatIds.stream().sorted().toList();
		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllByIdForUpdate(sortedSeatIds);

		Preconditions.validate(scheduledSeats.size() == sortedSeatIds.size(), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(
			scheduledSeats.stream().allMatch(seat -> showScheduleId.equals(seat.getShowScheduleId())),
			ErrorCode.NOT_CORRECT_SEAT
		);
		Preconditions.validate(
			scheduledSeats.stream().allMatch(ScheduledSeat::isAvailable),
			ErrorCode.ALREADY_HOLD_SEAT
		);

		ShowScheduled showScheduled = showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

		Reservation reservation = Reservation.create(
			userId,
			reservationNumber,
			createGradeSummary(scheduledSeats),
			createShowInfo(showScheduled)
		);
		List<Ticket> tickets = createTickets(scheduledSeats, reservation);
		reservation.addTickets(tickets);

		LocalDateTime reservedAt = LocalDateTime.now();
		scheduledSeats.forEach(seat -> seat.reserve(reservationNumber, reservedAt));
		reservationRepository.saveAndFlush(reservation);

		return new BookingResponse.Create(reservationNumber);
	}

	private List<Ticket> createTickets(List<ScheduledSeat> scheduledSeats, Reservation reservation) {
		return scheduledSeats.stream()
			.map(scheduledSeat -> {
				Seat seat = scheduledSeat.getSeat();
				SeatSection section = seat.getSeatSection();
				return Ticket.create(
					reservation,
					NumberGenerator.generateTicketNumber(),
					section.getGradeName(),
					section.getPrice(),
					createSeatDetail(section, seat),
					scheduledSeat.getId()
				);
			})
			.toList();
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

	private String createSeatDetail(SeatSection section, Seat seat) {
		return SEAT_DETAIL_FORMAT.formatted(
			section.getFloor(),
			section.getName(),
			seat.getSeatRow(),
			seat.getSeatNumber()
		);
	}
}

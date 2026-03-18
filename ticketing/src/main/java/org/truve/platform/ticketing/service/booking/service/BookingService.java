package org.truve.platform.ticketing.service.booking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.TicketingClient;
import org.truve.platform.ticketing.service.booking.external.client.TicketingResponse;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.service.util.NumberGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {
	private static final Long TICKET_SERVICE_FEE = 2000L;
	private static final String GRADE_SUMMARY_FORMAT = "%s석 %d인";
	private static final String LINE_BREAK = "\n";
	private static final String SEAT_DETAIL_FORMAT = "%d층 %s구역 %s열 %d번";

	private final ReservationRepository reservationRepository;
	private final TicketingClient ticketingClient;
	private final NumberGenerator numberGenerator;
	private final PaymentPublisher paymentPublisher;

	@Transactional
	public BookingResponse.Create create(UUID userId, BookingRequest.Create request) {
		TicketingResponse.SeatInfo seatInfo = ticketingClient.getSeatInfo(request.getSeatIds());

		Reservation reservation = createReservation(userId, seatInfo);
		List<Ticket> tickets = createTickets(seatInfo, reservation);
		reservation.addTickets(tickets);

		reservationRepository.save(reservation);
		return new BookingResponse.Create(reservation.getNumber());
	}

	private Reservation createReservation(UUID userId, TicketingResponse.SeatInfo seatInfo) {
		return Reservation.create(
			userId,
			numberGenerator.generateReservationNumber(),
			calculateTotalAmount(seatInfo),
			TICKET_SERVICE_FEE * seatInfo.getSeats().size(),
			createGradeSummary(seatInfo),
			createShowInfo(seatInfo)
		);
	}

	private List<Ticket> createTickets(TicketingResponse.SeatInfo seatInfo, Reservation reservation) {
		return seatInfo.getSeats().stream().map(
			seat -> Ticket.create(
				reservation,
				numberGenerator.generateTicketNumber(),
				seat.getGradeName(),
				seat.getPrice(),
				createSeatDetail(seat)
			)
		).toList();
	}

	private Long calculateTotalAmount(TicketingResponse.SeatInfo seatInfo) {
		return seatInfo.getSeats().stream()
			.map(TicketingResponse.Seat::getPrice)
			.reduce(0L, Long::sum);
	}

	private String createGradeSummary(TicketingResponse.SeatInfo seatInfo) {
		return seatInfo.getSeats().stream()
			.collect(Collectors.groupingBy(
				TicketingResponse.Seat::getGradeName,
				LinkedHashMap::new,
				Collectors.counting()
			))
			.entrySet().stream()
			.map(e -> GRADE_SUMMARY_FORMAT.formatted(e.getKey(), e.getValue()))
			.collect(Collectors.joining(LINE_BREAK));
	}

	private ShowInfo createShowInfo(TicketingResponse.SeatInfo seatInfo) {
		return ShowInfo.builder()
			.showId(seatInfo.getShowId())
			.title(seatInfo.getShowTitle())
			.venueName(seatInfo.getVenueName())
			.startAt(seatInfo.getStartAt())
			.posterImg(seatInfo.getPosterImg())
			.build();
	}

	private String createSeatDetail(TicketingResponse.Seat seat) {
		return SEAT_DETAIL_FORMAT.formatted(
			seat.getFloor(),
			seat.getSectionName(),
			seat.getSeatRow(),
			seat.getSeatNumber()
		);
	}

	@Transactional
	public void paymentReady(String reservationNumber, BookingRequest.ApplicantInfo request) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);

		reservation.readyForPayment(request.toEntity());

		paymentPublisher.publish(PaymentEventCommand.Create.of(reservation));
	}

	@Transactional
	public void confirm(BookingEventCommand.Confirmed event) {
		Reservation reservation = reservationRepository.findByNumber(event.getReservationNumber());
		reservation.confirm(event.getPaidAt(), event.isDepositPending());
	}

	@Transactional
	public void depositReceive(BookingEventCommand.DepositReceived event) {
		Reservation reservation = reservationRepository.findByNumber(event.getReservationNumber());
		reservation.depositReceive(event.getPaidAt());
	}
}

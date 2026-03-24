package org.truve.platform.ticketing.service.booking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.VirtualAccount;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.TicketingClient;
import org.truve.platform.ticketing.service.booking.external.client.TicketingResponse;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {
	private static final String GRADE_SUMMARY_FORMAT = "%s석 %d인";
	private static final String LINE_BREAK = "\n";
	private static final String SEAT_DETAIL_FORMAT = "%d층 %s구역 %s열 %d번";

	private final ReservationRepository reservationRepository;
	private final TicketingClient ticketingClient;
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
			NumberGenerator.generateReservationNumber(),
			createGradeSummary(seatInfo),
			createShowInfo(seatInfo)
		);
	}

	private List<Ticket> createTickets(TicketingResponse.SeatInfo seatInfo, Reservation reservation) {
		return seatInfo.getSeats().stream().map(
			seat -> Ticket.create(
				reservation,
				NumberGenerator.generateTicketNumber(),
				seat.getGradeName(),
				seat.getPrice(),
				createSeatDetail(seat)
			)
		).toList();
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
	}

	@Transactional
	public void depositReceive(BookingEventCommand.DepositReceived event) {
		Reservation reservation = reservationRepository.findByNumber(event.getReservationNumber());
		reservation.depositReceive(event.getPaidAt());
	}

	@Transactional
	public BookingResponse.Cancel getCancel(String reservationNumber, List<Long> ticketIds) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);

		List<Long> resolvedTicketIds = ticketIds != null ? ticketIds
			: reservation.getTickets().stream().map(Ticket::getId).toList();

		return BookingResponse.Cancel.from(reservation, resolvedTicketIds, LocalDateTime.now());
	}
}

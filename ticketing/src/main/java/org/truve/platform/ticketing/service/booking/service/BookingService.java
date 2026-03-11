package org.truve.platform.ticketing.service.booking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.TicketingClient;
import org.truve.platform.ticketing.service.booking.external.client.TicketingResponse;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.service.util.NumberGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {
	private static final String GRADE_SUMMARY_FORMAT = "%s석 %d인";
	private static final String LINE_BREAK = "\n";
	private static final String SEAT_DETAIL_FORMAT = "%d층 %s구역 %s열 %d번";

	private final ReservationRepository reservationRepository;
	private final TicketingClient ticketingClient;
	private final NumberGenerator numberGenerator;
	private final PaymentPublisher paymentPublisher;

	@Transactional
	public BookingResponse.Create create(UUID userId, BookingRequest.Create request) {
		List<TicketingResponse.SeatInfo> seatInfos = ticketingClient.getSeatInfos(request.getSeatIds());

		Reservation reservation = createReservation(userId, seatInfos);
		List<Ticket> tickets = createTickets(seatInfos, reservation);
		reservation.addTickets(tickets);

		reservationRepository.save(reservation);
		return new BookingResponse.Create(reservation.getNumber());
	}

	private Reservation createReservation(UUID userId, List<TicketingResponse.SeatInfo> seatInfos) {
		return Reservation.create(
			userId,
			numberGenerator.generateReservationNumber(),
			calculateTotalAmount(seatInfos),
			createGradeSummary(seatInfos)
		);
	}

	private List<Ticket> createTickets(List<TicketingResponse.SeatInfo> seatInfos, Reservation reservation) {
		return seatInfos.stream().map(
			seatInfo -> Ticket.create(
				reservation,
				numberGenerator.generateTicketNumber(),
				seatInfo.getPrice(),
				createSeatDetail(seatInfo)
			)
		).toList();
	}

	private Long calculateTotalAmount(List<TicketingResponse.SeatInfo> seatInfos) {
		return seatInfos.stream()
			.map(TicketingResponse.SeatInfo::getPrice)
			.reduce(0L, Long::sum);
	}

	private String createGradeSummary(List<TicketingResponse.SeatInfo> seatInfos) {
		return seatInfos.stream()
			.collect(Collectors.groupingBy(
				TicketingResponse.SeatInfo::getGradeName,
				LinkedHashMap::new,
				Collectors.counting()
			))
			.entrySet().stream()
			.map(e -> GRADE_SUMMARY_FORMAT.formatted(e.getKey(), e.getValue()))
			.collect(Collectors.joining(LINE_BREAK));
	}

	private String createSeatDetail(TicketingResponse.SeatInfo seatInfo) {
		return SEAT_DETAIL_FORMAT.formatted(
			seatInfo.getFloor(),
			seatInfo.getSectionName(),
			seatInfo.getSeatRow(),
			seatInfo.getSeatNumber()
		);
	}

	@Transactional
	public void paymentReady(String reservationNumber, BookingRequest.ApplicantInfo request) {
		Reservation reservation = reservationRepository.findByNumber(reservationNumber);

		reservation.readyForPayment(request.toEntity());

		paymentPublisher.publish(PaymentEventCommand.Create.of(reservation));
	}
}

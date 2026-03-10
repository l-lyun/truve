package org.truve.platform.ticketing.service.ticket.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.ticket.client.TicketingClient;
import org.truve.platform.ticketing.service.ticket.client.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticket.domain.entity.Reservation;
import org.truve.platform.ticketing.service.ticket.domain.entity.Ticket;
import org.truve.platform.ticketing.service.ticket.dto.TicketRequest;
import org.truve.platform.ticketing.service.ticket.dto.TicketResponse;
import org.truve.platform.ticketing.service.ticket.repository.ReservationRepository;
import org.truve.platform.ticketing.service.ticket.service.util.NumberGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketService {
	private static final String GRADE_SUMMARY_FORMAT = "%s석 %d인";
	private static final String LINE_BREAK = "\n";
	private static final String SEAT_DETAIL_FORMAT = "%d층 %s구역 %s열 %d번";

	private final ReservationRepository reservationRepository;
	private final TicketingClient ticketingClient;
	private final NumberGenerator numberGenerator;

	@Transactional
	public TicketResponse.Create create(Long userId, TicketRequest.Create request) {
		List<TicketingResponse.SeatInfo> seatInfos = ticketingClient.getSeatInfos(request.getSeatIds());

		Reservation reservation = createReservation(userId, seatInfos);
		List<Ticket> tickets = createTickets(seatInfos, reservation);
		reservation.addTickets(tickets);

		reservationRepository.save(reservation);
		return new TicketResponse.Create(reservation.getNumber());
	}

	private Reservation createReservation(Long userId, List<TicketingResponse.SeatInfo> seatInfos) {
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
}

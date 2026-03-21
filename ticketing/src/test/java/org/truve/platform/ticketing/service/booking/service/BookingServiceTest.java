package org.truve.platform.ticketing.service.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.TicketingClient;
import org.truve.platform.ticketing.service.booking.external.client.TicketingResponse;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.service.util.NumberGenerator;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private TicketingClient ticketingClient;
	@Mock
	private NumberGenerator numberGenerator;

	@InjectMocks
	private BookingService bookingService;

	@Test
	@DisplayName("예매 내역과 티켓을 생성하고 예매 번호를 반환한다.")
	void 예매생성_성공() {
		// given
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		List<Long> seatIds = List.of(10L, 11L, 12L);
		BookingRequest.Create request = new BookingRequest.Create(seatIds);

		TicketingResponse.Seat seat1 = new TicketingResponse.Seat("Section1", 1L, "VIP", "A", 10L, 10000L);
		TicketingResponse.Seat seat2 = new TicketingResponse.Seat("Section2", 2L, "S", "B", 20L, 20000L);
		TicketingResponse.Seat seat3 = new TicketingResponse.Seat("Section3", 3L, "VIP", "C", 30L, 30000L);
		List<TicketingResponse.Seat> seats = List.of(seat1, seat2, seat3);
		TicketingResponse.SeatInfo seatInfo = new TicketingResponse.SeatInfo(
			1L,
			"title",
			"venue",
			LocalDateTime.now(),
			"poster",
			seats);

		String reservationNumber = "R20260309ABCDEF";
		String ticketNumber1 = "T-1234567890123";
		String ticketNumber2 = "T-9876543210987";
		String ticketNumber3 = "T-1111111111111";

		given(ticketingClient.getSeatInfo(seatIds)).willReturn(seatInfo);
		given(numberGenerator.generateReservationNumber()).willReturn(reservationNumber);
		given(numberGenerator.generateTicketNumber()).willReturn(ticketNumber1, ticketNumber2, ticketNumber3);

		// when
		BookingResponse.Create response = bookingService.create(userId, request);

		// then
		ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(captor.capture());
		Reservation savedReservation = captor.getValue();

		assertAll(
			() -> assertThat(response.getReservationNumber()).isEqualTo(reservationNumber),
			() -> assertThat(savedReservation.calculateTicketAmount()).isEqualTo(60000L),
			() -> assertThat(savedReservation.getGradeSummary()).isEqualTo("VIP석 2인\nS석 1인"),
			() -> assertThat(savedReservation.getTickets()).hasSize(3),
			() -> assertThat(savedReservation.getServiceFee()).isEqualTo(6000L),
			() -> {
				assertNotNull(savedReservation.getTickets());
				assertThat(savedReservation.getTickets().getFirst().getNumber()).isEqualTo(ticketNumber1);
				assertThat(savedReservation.getTickets().get(1).getPriceSnapshot()).isEqualTo(20000L);
				assertThat(savedReservation.getTickets().getLast().getStatus()).isEqualTo(TicketStatus.ISSUED);
				assertThat(savedReservation.getTickets().get(1).getUsedAt()).isNull();
				assertThat(savedReservation.getTickets().getFirst().getSeatDetail()).isEqualTo("1층 Section1구역 A열 10번");
			}
		);
	}

}

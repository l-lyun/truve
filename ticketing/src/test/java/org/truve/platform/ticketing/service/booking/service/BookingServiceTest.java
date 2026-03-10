package org.truve.platform.ticketing.service.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.client.TicketingClient;
import org.truve.platform.ticketing.service.booking.client.dto.TicketingResponse;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
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
		Long userId = 1L;
		List<Long> seatIds = List.of(10L, 11L, 12L);
		BookingRequest.Create request = new BookingRequest.Create(seatIds);

		TicketingResponse.SeatInfo seat1 = new TicketingResponse.SeatInfo("A", 1L, "VIP", "A", 13L, 10000L);
		TicketingResponse.SeatInfo seat2 = new TicketingResponse.SeatInfo("B", 2L, "VIP", "B", 14L, 20000L);
		TicketingResponse.SeatInfo seat3 = new TicketingResponse.SeatInfo("C", 3L, "S", "C", 15L, 30000L);
		List<TicketingResponse.SeatInfo> seatInfos = List.of(seat1, seat2, seat3);

		String reservationNumber = "R20260309ABCDEF";
		String ticketNumber1 = "T-1234567890123";
		String ticketNumber2 = "T-9876543210987";
		String ticketNumber3 = "T-1111111111111";

		given(ticketingClient.getSeatInfos(seatIds)).willReturn(seatInfos);
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
			() -> assertThat(savedReservation.getTotalAmount()).isEqualTo(60000L),
			() -> assertThat(savedReservation.getGradeSummary()).isEqualTo("VIP석 2인\nS석 1인"),
			() -> assertThat(savedReservation.getTickets()).hasSize(3),
			() -> {
				assertNotNull(savedReservation.getTickets());
				assertThat(savedReservation.getTickets().getFirst().getNumber()).isEqualTo(ticketNumber1);
				assertThat(savedReservation.getTickets().get(1).getPriceSnapshot()).isEqualTo(20000L);
				assertThat(savedReservation.getTickets().getLast().getStatus()).isEqualTo(TicketStatus.ISSUED);
				assertThat(savedReservation.getTickets().get(1).getUsedAt()).isNull();
				assertThat(savedReservation.getTickets().getFirst().getSeatDetail()).isEqualTo("1층 A구역 A열 13번");
			}
		);
	}

}
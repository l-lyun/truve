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
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.ticketing.TicketingClient;
import org.truve.platform.ticketing.service.booking.external.client.ticketing.TicketingResponse;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private TicketingClient ticketingClient;

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
		given(ticketingClient.getSeatInfo(seatIds)).willReturn(seatInfo);

		// when
		bookingService.create(userId, request);

		// then
		ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(captor.capture());
		Reservation savedReservation = captor.getValue();

		assertAll(
			() -> assertThat(savedReservation.calculateTicketAmount()).isEqualTo(60000L),
			() -> assertThat(savedReservation.getGradeSummary()).isEqualTo("VIP석 2인\nS석 1인"),
			() -> assertThat(savedReservation.getTickets()).hasSize(3),
			() -> assertThat(savedReservation.getServiceFee()).isEqualTo(6000L),
			() -> {
				assertNotNull(savedReservation.getTickets());
				assertThat(savedReservation.getTickets().get(1).getPriceSnapshot()).isEqualTo(20000L);
				assertThat(savedReservation.getTickets().getLast().getStatus()).isEqualTo(TicketStatus.ISSUED);
				assertThat(savedReservation.getTickets().get(1).getUsedAt()).isNull();
				assertThat(savedReservation.getTickets().getFirst().getSeatDetail()).isEqualTo("1층 Section1구역 A열 10번");
			}
		);
	}

	@Test
	@DisplayName("ticketIds가 null이면 전체 티켓 목록을 반환한다.")
	void 전체티켓목록_반환_성공() {
		// given
		Reservation reservation = createReservation();
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		// when
		BookingResponse.Cancel res = bookingService.getCancel("R-001", null);

		// then
		assertThat(res.getTickets()).hasSize(2);
	}

	@Test
	@DisplayName("ticketIds가 null이 아니어도 전체 티켓 목록을 반환한다.")
	void 특정티켓선택시_전체티켓목록_반환_성공() {
		// given
		Reservation reservation = createReservation();
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		// when
		BookingResponse.Cancel res = bookingService.getCancel("R-001", List.of(1L));

		// then
		assertThat(res.getTickets()).hasSize(2);
	}

	private Reservation createReservation() {
		Reservation reservation = Reservation.create(
			UUID.randomUUID(),
			"R-001",
			"VIP석 2인",
			ShowInfo.builder()
				.showId(1L)
				.title("킹키부츠")
				.startAt(LocalDateTime.now().plusDays(30))
				.build()
		);

		Ticket ticket1 = Ticket.create(reservation, "T-001", "VIP", 120000L, "1층 A구역 1열 1번");
		ReflectionTestUtils.setField(ticket1, "id", 1L);
		Ticket ticket2 = Ticket.create(reservation, "T-002", "VIP", 120000L, "1층 A구역 1열 2번");
		ReflectionTestUtils.setField(ticket2, "id", 2L);

		List<Ticket> tickets = List.of(ticket1, ticket2);
		reservation.addTickets(tickets);
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);

		return reservation;
	}
}

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
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentClient;
import org.truve.platform.ticketing.service.booking.external.client.ticketing.TicketingClient;
import org.truve.platform.ticketing.service.booking.external.client.ticketing.TicketingResponse;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingPublisher;
import org.truve.platform.ticketing.service.booking.risk.service.BookingBotRiskService;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private TicketingClient ticketingClient;
	@Mock
	private TicketingPublisher ticketingPublisher;
	@Mock
	private PaymentPublisher paymentPublisher;
	@Mock
	private PaymentClient paymentClient;
	@Mock
	private BookingBotRiskService bookingBotRiskService;

	@InjectMocks
	private BookingService bookingService;

	@Test
	@DisplayName("예매 내역과 티켓을 생성하고 예매 번호를 반환한다.")
	void 예매생성_성공() {
		// given
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		List<Long> seatIds = List.of(10L, 11L, 12L);
		BookingRequest.Create request = new BookingRequest.Create(seatIds);

		TicketingResponse.Seat seat1 = new TicketingResponse.Seat(10L, "Section1", 1L, "VIP", "A", 10L, 10000L);
		TicketingResponse.Seat seat2 = new TicketingResponse.Seat(11L, "Section2", 2L, "S", "B", 20L, 20000L);
		TicketingResponse.Seat seat3 = new TicketingResponse.Seat(12L, "Section3", 3L, "VIP", "C", 30L, 30000L);
		List<TicketingResponse.Seat> seats = List.of(seat1, seat2, seat3);
		TicketingResponse.SeatInfo seatInfo = new TicketingResponse.SeatInfo(
			1L,
			100L,
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
		ArgumentCaptor<TicketingEventCommand.TicketingEvent> eventCaptor =
			ArgumentCaptor.forClass(TicketingEventCommand.TicketingEvent.class);
		verify(reservationRepository).save(captor.capture());
		verify(ticketingPublisher).publish(eventCaptor.capture());
		Reservation savedReservation = captor.getValue();
		TicketingEventCommand.HoldRequested holdRequested =
			(TicketingEventCommand.HoldRequested)eventCaptor.getValue();

		assertAll(
			() -> assertThat(savedReservation.calculateTicketAmount()).isEqualTo(60000L),
			() -> assertThat(savedReservation.getGradeSummary()).isEqualTo("VIP석 2인\nS석 1인"),
			() -> assertThat(savedReservation.getShowInfo().getShowScheduleId()).isEqualTo(100L),
			() -> assertThat(savedReservation.getTickets()).hasSize(3),
			() -> assertThat(savedReservation.getServiceFee()).isEqualTo(6000L),
			() -> assertThat(savedReservation.getTickets().getFirst().getScheduledSeatId()).isEqualTo(10L),
			() -> assertThat(holdRequested.getReservationNumber()).isEqualTo(savedReservation.getNumber()),
			() -> assertThat(holdRequested.getUserId()).isEqualTo(userId),
			() -> assertThat(holdRequested.getScheduledSeatIds()).containsExactlyElementsOf(seatIds),
			() -> {
				assertNotNull(savedReservation.getTickets());
				assertThat(savedReservation.getTickets().get(1).getPriceSnapshot()).isEqualTo(20000L);
				assertThat(savedReservation.getTickets().getLast().getStatus()).isEqualTo(TicketStatus.PENDING);
				assertThat(savedReservation.getTickets().get(1).getUsedAt()).isNull();
				assertThat(savedReservation.getTickets().getFirst().getSeatDetail()).isEqualTo("1층 Section1구역 A열 10번");
			}
		);
	}

	@Test
	@DisplayName("결제가 확정되면 예약 상태를 업데이트하고 SOLD_CONFIRMED 이벤트를 발행한다.")
	void 결제확정_SOLD_이벤트_발행() {
		// given
		Reservation reservation = createReservation();
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		BookingEventCommand.Confirmed event = new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);

		// when
		bookingService.confirm(event);

		// then
		ArgumentCaptor<TicketingEventCommand.TicketingEvent> eventCaptor =
			ArgumentCaptor.forClass(TicketingEventCommand.TicketingEvent.class);
		verify(ticketingPublisher).publish(eventCaptor.capture());

		TicketingEventCommand.SoldConfirmed soldConfirmed =
			(TicketingEventCommand.SoldConfirmed)eventCaptor.getValue();
		assertAll(
			() -> assertThat(soldConfirmed.getReservationNumber()).isEqualTo("R-001"),
			() -> assertThat(soldConfirmed.getScheduledSeatIds()).containsExactly(1L, 2L),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
		);
	}

	@Test
	@DisplayName("위험 사용자 차단이 없으면 paymentReady는 정상적으로 결제 생성 이벤트를 발행한다.")
	void 결제준비_정상통과() {
		// given
		Reservation reservation = createReservation();
		BookingRequest.ApplicantInfo request = new BookingRequest.ApplicantInfo(
			"홍길동",
			"19900101",
			"test@test.com",
			"01012341234"
		);
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		// when
		bookingService.paymentReady("R-001", request);

		// then
		ArgumentCaptor<PaymentEventCommand.Create> paymentEventCaptor =
			ArgumentCaptor.forClass(PaymentEventCommand.Create.class);
		verify(paymentPublisher).publish(paymentEventCaptor.capture());

		assertAll(
			() -> verify(bookingBotRiskService).validatePaymentReady(reservation.getUserId()),
			() -> assertThat(paymentEventCaptor.getValue().getOrderId()).isEqualTo(reservation.getNumber()),
			() -> assertThat(paymentEventCaptor.getValue().getAmount()).isEqualTo(reservation.getTotalAmount()),
			() -> assertThat(reservation.getStatus()).isEqualTo(org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus.PENDING_PAYMENT)
		);
	}

	@Test
	@DisplayName("ticketIds가 null이면 전체 티켓 목록을 반환한다.")
	void 전체티켓목록_반환_성공() {
		// given
		Reservation reservation = createReservation();
		confirmByCard(reservation);
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
		confirmByCard(reservation);
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		// when
		BookingResponse.Cancel res = bookingService.getCancel("R-001", List.of(1L));

		// then
		assertThat(res.getTickets()).hasSize(2);
	}

	@Test
	@DisplayName("예매 취소 시 결제 취소 후 HOLD_RELEASED 이벤트를 발행한다.")
	void 예매취소_좌석해제이벤트발행_성공() {
		Reservation reservation = createReservation();
		confirmByCard(reservation);
		BookingRequest.Cancel request = new BookingRequest.Cancel("단순변심", List.of(1L));
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		BookingResponse.CanceledTickets response = bookingService.cancel("R-001", request);

		ArgumentCaptor<TicketingEventCommand.TicketingEvent> eventCaptor =
			ArgumentCaptor.forClass(TicketingEventCommand.TicketingEvent.class);
		verify(paymentClient).cancel(eq("R-001"), anyString(), any());
		verify(ticketingPublisher).publish(eventCaptor.capture());

		TicketingEventCommand.HoldReleased holdReleased =
			(TicketingEventCommand.HoldReleased)eventCaptor.getValue();

		assertAll(
			() -> assertThat(response.getCanceledTicketIds()).containsExactly(1L),
			() -> assertThat(holdReleased.getReservationNumber()).isEqualTo("R-001"),
			() -> assertThat(holdReleased.getScheduledSeatIds()).containsExactly(1L),
			() -> assertThat(reservation.getTickets().getFirst().isCanceled()).isTrue(),
			() -> assertThat(reservation.getTickets().getLast().isCanceled()).isFalse()
		);
	}

	private Reservation createReservation() {
		Reservation reservation = Reservation.create(
			UUID.randomUUID(),
			"R-001",
			"VIP석 2인",
			ShowInfo.builder()
				.showId(1L)
				.showScheduleId(100L)
				.title("킹키부츠")
				.startAt(LocalDateTime.now().plusDays(30))
				.build()
		);

		Ticket ticket1 = Ticket.create(reservation, "T-001", "VIP", 120000L, "1층 A구역 1열 1번", 1L);
		ReflectionTestUtils.setField(ticket1, "id", 1L);
		Ticket ticket2 = Ticket.create(reservation, "T-002", "VIP", 120000L, "1층 A구역 1열 2번", 2L);
		ReflectionTestUtils.setField(ticket2, "id", 2L);

		List<Ticket> tickets = List.of(ticket1, ticket2);
		reservation.addTickets(tickets);
		return reservation;
	}

	private void confirmByCard(Reservation reservation) {
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
	}
}

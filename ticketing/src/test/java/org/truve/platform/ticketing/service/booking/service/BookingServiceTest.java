package org.truve.platform.ticketing.service.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.VirtualAccount;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentClient;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingPublisher;
import org.truve.platform.ticketing.service.booking.risk.service.BookingBotRiskService;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.ticketing.service.SeatHoldService;
import org.truve.platform.ticketing.service.ticketing.service.SeatHoldLockService;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private BookingCreationService bookingCreationService;
	@Mock
	private BookingLockService bookingLockService;
	@Mock
	private SeatHoldService seatHoldService;
	@Mock
	private SeatHoldLockService seatHoldLockService;
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

	@BeforeEach
	void setUpSeatHoldLock() {
		lenient().when(seatHoldLockService.acquire(any(), anyLong()))
			.thenAnswer(invocation -> new SeatHoldLockService.SeatHoldLock(
				invocation.getArgument(0),
				invocation.getArgument(1),
				"seat-hold-lock-token"
			));
		lenient().when(seatHoldLockService.release(any())).thenReturn(true);
	}

	@Test
	@DisplayName("예매 내역과 티켓을 생성하고 예매 번호를 반환한다.")
	void 예매생성_성공() {
		// given
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		List<Long> seatIds = List.of(10L, 11L, 12L);
		BookingRequest.Create request = new BookingRequest.Create(100L, seatIds);
		BookingLockService.BookingLock bookingLock = new BookingLockService.BookingLock(userId, 100L, "lock-token");
		SeatHoldService.SeatClaim claim = new SeatHoldService.SeatClaim(
			100L, seatIds, "session-token", "claim-token"
		);
		given(bookingLockService.acquire(userId, 100L)).willReturn(bookingLock);
		given(reservationRepository.existsBlockingBooking(userId, 100L)).willReturn(false);
		given(seatHoldService.claim(eq(100L), eq(seatIds), eq("session-token"), anyString())).willReturn(claim);
		given(bookingCreationService.create(eq(userId), eq(100L), eq(seatIds), anyString()))
			.willAnswer(invocation -> new BookingResponse.Create(invocation.getArgument(3)));

		// when
		BookingResponse.Create response = bookingService.create(userId, "session-token", request);

		// then
		InOrder lockBoundary = inOrder(seatHoldLockService, bookingCreationService);
		assertAll(
			() -> assertThat(response.getReservationNumber()).isNotBlank(),
			() -> verify(seatHoldService).validateSession(userId, 100L, "session-token"),
			() -> verify(seatHoldLockService).acquire(userId, 100L),
			() -> verify(seatHoldLockService).release(any()),
			() -> verify(seatHoldService).release(claim),
			() -> verify(bookingLockService).release(bookingLock),
			() -> verify(ticketingPublisher, never()).publish(any())
		);
		lockBoundary.verify(seatHoldLockService).acquire(userId, 100L);
		lockBoundary.verify(seatHoldLockService).release(any());
		lockBoundary.verify(bookingCreationService).create(eq(userId), eq(100L), eq(seatIds), anyString());
	}

	@Test
	@DisplayName("같은 사용자의 동일 회차 활성 예약이 있으면 새 예약을 만들지 않는다.")
	void 예매생성_기존활성예약_차단() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingRequest.Create request = new BookingRequest.Create(100L, List.of(10L));
		BookingLockService.BookingLock bookingLock = new BookingLockService.BookingLock(userId, 100L, "lock-token");
		given(bookingLockService.acquire(userId, 100L)).willReturn(bookingLock);
		given(reservationRepository.existsBlockingBooking(userId, 100L)).willReturn(true);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> bookingService.create(userId, "session-token", request)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_BOOKED_SHOW);
		verify(seatHoldService, never()).claim(anyLong(), anyList(), anyString(), anyString());
		verify(bookingCreationService, never()).create(any(), anyLong(), anyList(), anyString());
		verify(bookingLockService).release(bookingLock);
	}

	@Test
	@DisplayName("좌석 ID 목록에 null이 있으면 Redis와 DB 작업을 시작하지 않는다.")
	void 예매생성_null좌석ID_차단() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingRequest.Create request = new BookingRequest.Create(100L, Collections.singletonList(null));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> bookingService.create(userId, "session-token", request)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_SEAT);
		verify(seatHoldService, never()).validateSession(any(), anyLong(), anyString());
		verify(bookingLockService, never()).acquire(any(), anyLong());
		verify(bookingCreationService, never()).create(any(), anyLong(), anyList(), anyString());
	}

	@Test
	@DisplayName("DB 예약 생성이 실패하면 좌석 claim을 기존 세션으로 복원하고 사용자 락을 정리한다.")
	void 예매생성_DB실패_보상() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		List<Long> seatIds = List.of(10L, 11L);
		BookingRequest.Create request = new BookingRequest.Create(100L, seatIds);
		BookingLockService.BookingLock bookingLock = new BookingLockService.BookingLock(userId, 100L, "lock-token");
		SeatHoldService.SeatClaim claim = new SeatHoldService.SeatClaim(
			100L, seatIds, "session-token", "claim-token"
		);
		given(bookingLockService.acquire(userId, 100L)).willReturn(bookingLock);
		given(seatHoldService.claim(eq(100L), eq(seatIds), eq("session-token"), anyString())).willReturn(claim);
		given(bookingCreationService.create(eq(userId), eq(100L), eq(seatIds), anyString()))
			.willThrow(new CustomException(ErrorCode.ALREADY_HOLD_SEAT));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> bookingService.create(userId, "session-token", request)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_HOLD_SEAT);
		verify(seatHoldService).restore(claim);
		verify(seatHoldService, never()).release(claim);
		verify(bookingLockService).release(bookingLock);
	}

	@Test
	@DisplayName("결제가 확정되면 예약 상태를 업데이트하고 SOLD_CONFIRMED 이벤트를 발행한다.")
	void 결제확정_SOLD_이벤트_발행() {
		// given
		Reservation reservation = createReservation();
		reservation.readyForPayment(null);
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
	@DisplayName("같은 카드 결제 결과를 반복해서 받아도 SOLD_CONFIRMED는 한 번만 발행한다.")
	void 카드결제_의미중복_SOLD_한번만_발행() {
		Reservation reservation = createReservation();
		reservation.readyForPayment(null);
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);
		BookingEventCommand.Confirmed event = new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);

		bookingService.confirm(event);
		bookingService.confirm(event);

		verify(ticketingPublisher, times(1)).publish(any(TicketingEventCommand.SoldConfirmed.class));
	}

	@Test
	@DisplayName("가상계좌 발급 단계에서는 티켓을 발급하거나 SOLD_CONFIRMED를 발행하지 않는다.")
	void 가상계좌발급_SOLD_이벤트_미발행() {
		Reservation reservation = createReservation();
		reservation.readyForPayment(null);
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);
		LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
		BookingEventCommand.Confirmed event = new BookingEventCommand.Confirmed(
			"R-001",
			LocalDateTime.now(),
			null,
			"가상계좌",
			new BookingEventCommand.Confirmed.VirtualAccount("111-111", "은행", "홍길동", dueDate)
		);

		bookingService.confirm(event);

		assertAll(
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_DEPOSIT),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING),
			() -> verify(ticketingPublisher, never()).publish(any())
		);
	}

	@Test
	@DisplayName("가상계좌 입금 완료 후 티켓을 발급하고 SOLD_CONFIRMED를 발행한다.")
	void 가상계좌입금완료_SOLD_이벤트_발행() {
		Reservation reservation = createReservation();
		reservation.readyForPayment(null);
		LocalDateTime now = LocalDateTime.now();
		reservation.confirm(
			now,
			null,
			"가상계좌",
			new VirtualAccount(
				"111-111", "은행", "홍길동", now.plusDays(1)
			)
		);
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);

		bookingService.depositReceive(new BookingEventCommand.DepositReceived("R-001", now));

		assertAll(
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED),
			() -> verify(ticketingPublisher).publish(argThat(
				event -> event instanceof TicketingEventCommand.SoldConfirmed
			))
		);
	}

	@Test
	@DisplayName("가상계좌 입금 완료 이벤트가 중복돼도 SOLD_CONFIRMED는 한 번만 발행한다.")
	void 가상계좌입금_의미중복_SOLD_한번만_발행() {
		Reservation reservation = createReservation();
		reservation.readyForPayment(null);
		LocalDateTime now = LocalDateTime.now();
		reservation.confirm(
			now,
			null,
			"가상계좌",
			new VirtualAccount(
				"111-111", "은행", "홍길동", now.plusDays(1)
			)
		);
		given(reservationRepository.findByNumber("R-001")).willReturn(reservation);
		BookingEventCommand.DepositReceived event = new BookingEventCommand.DepositReceived("R-001", now);

		bookingService.depositReceive(event);
		bookingService.depositReceive(event);

		verify(ticketingPublisher, times(1)).publish(any(TicketingEventCommand.SoldConfirmed.class));
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
		given(reservationRepository.findByNumberForUpdate("R-001")).willReturn(reservation);

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
			() -> assertThat(reservation.getTickets().getLast().isCanceled()).isFalse(),
			() -> assertThat(reservation.getBlockBooking()).isTrue()
		);
	}

	@Test
	@DisplayName("모든 티켓을 취소하면 blockBooking을 null로 변경한다.")
	void 예매전체취소_blockBooking해제() {
		Reservation reservation = createReservation();
		confirmByCard(reservation);
		BookingRequest.Cancel request = new BookingRequest.Cancel("단순변심", List.of(1L, 2L));
		given(reservationRepository.findByNumberForUpdate("R-001")).willReturn(reservation);

		bookingService.cancel("R-001", request);

		assertThat(reservation.getBlockBooking()).isNull();
	}

	@Test
	@DisplayName("이미 취소된 티켓이 포함된 요청은 결제 취소를 호출하지 않는다.")
	void 이미_취소된_티켓_중복환불_차단() {
		Reservation reservation = createReservation();
		confirmByCard(reservation);
		reservation.cancel(List.of(1L), LocalDateTime.now());
		BookingRequest.Cancel request = new BookingRequest.Cancel("단순변심", List.of(1L, 2L));
		given(reservationRepository.findByNumberForUpdate("R-001")).willReturn(reservation);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> bookingService.cancel("R-001", request)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_CANCELED_TICKET);
		verify(paymentClient, never()).cancel(anyString(), anyString(), any());
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
		reservation.readyForPayment(null);
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
	}
}

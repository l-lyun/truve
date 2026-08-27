package org.truve.platform.ticketing.service.booking.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.VirtualAccount;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation.PaymentTransitionResult;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

public class ReservationTest {
	@Test
	@DisplayName("기존 예약 생성 팩토리는 CREATED 상태와 비어 있는 선점 정보를 유지한다.")
	void 기존_예약_생성() {
		Reservation reservation = createReservation();

		assertAll(
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CREATED),
			() -> assertThat(reservation.getHoldId()).isNull(),
			() -> assertThat(reservation.getExpiresAt()).isNull()
		);
	}

	@Test
	@DisplayName("비동기 좌석 선점 예약은 HOLD_PENDING 상태와 선점 정보를 저장한다.")
	void 좌석_선점_대기_예약_생성() {
		UUID userId = UUID.randomUUID();
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
		ShowInfo showInfo = createShowInfo();

		Reservation reservation = Reservation.createHoldPending(
			userId,
			"R-HOLD-001",
			"VIP석 2인",
			showInfo,
			"H-001",
			"seat-fingerprint",
			expiresAt
		);

		assertAll(
			() -> assertThat(reservation.getUserId()).isEqualTo(userId),
			() -> assertThat(reservation.getNumber()).isEqualTo("R-HOLD-001"),
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HOLD_PENDING),
			() -> assertThat(reservation.getHoldId()).isEqualTo("H-001"),
			() -> assertThat(reservation.getHoldRequestFingerprint()).isEqualTo("seat-fingerprint"),
			() -> assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt),
			() -> assertThat(reservation.getShowInfo()).isEqualTo(showInfo)
		);
	}

	@Test
	@DisplayName("비동기 좌석 선점 예약은 holdId와 만료 시각이 반드시 필요하다.")
	void 좌석_선점_대기_예약의_필수값_검증() {
		assertThatThrownBy(() -> Reservation.createHoldPending(
			UUID.randomUUID(), "R-HOLD-001", "VIP석 1인", createShowInfo(), " ", " ", null))
			.isInstanceOf(CustomException.class)
			.satisfies(exception -> assertThat(((CustomException)exception).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_BOOKING_SEAT_HOLD));
	}

	@Test
	@DisplayName("DB 좌석 HOLD가 완료되기 전에는 결제 대기 상태로 전이할 수 없다.")
	void 좌석_선점_반영_중에는_결제를_시작할_수_없다() {
		Reservation reservation = Reservation.createHoldPending(
			UUID.randomUUID(), "R-HOLD-001", "VIP석 1인", createShowInfo(), "H-001",
			"seat-fingerprint",
			LocalDateTime.now().plusMinutes(10));

		assertThatThrownBy(() -> reservation.readyForPayment(null))
			.isInstanceOf(CustomException.class)
			.satisfies(exception -> assertThat(((CustomException)exception).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS));
	}

	@Test
	@DisplayName("결제 생성 이벤트 재발행을 위해 PENDING_PAYMENT 상태에서 재시도할 수 있다.")
	void 결제_대기_상태에서_결제_준비를_재시도할_수_있다() {
		Reservation reservation = createReservation();

		reservation.readyForPayment(null);
		reservation.readyForPayment(null);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
	}

	@Test
	@DisplayName("티켓 추가 시 총 금액은 서비스 수수료(2000*티켓 수) + 티켓 가격 총합으로 계산된다.")
	void 티켓추가_총금액_계산() {
		// given
		int size = 2;
		Reservation reservation = createReservation();
		List<Ticket> tickets = createTickets(reservation, size);

		// when
		reservation.addTickets(tickets);

		// then
		assertAll(
			() -> assertThat(reservation.getServiceFee()).isEqualTo(4000L),
			() -> assertThat(reservation.getTotalAmount()).isEqualTo(24000L)
		);
	}

	@Test
	@DisplayName("무통장 입금 외 결제를 승인하면 상태가 CONFIRMED로 변경된다.")
	void 결제승인_일반() {
		// given
		Reservation reservation = createReservationWithTickets();
		LocalDateTime now = LocalDateTime.now();
		reservation.readyForPayment(null);

		// when
		reservation.confirm(now, now, "카드", null);

		// then
		assertAll(
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
		);
	}

	@Test
	@DisplayName("무통장 입금 결제를 승인하면 상태가 PENDING_DEPOSIT으로 변경되고 가상계좌 정보를 저장한다.")
	void 결제승인_무통장입금() {
		// given
		Reservation reservation = createReservationWithTickets();
		LocalDateTime now = LocalDateTime.now();
		VirtualAccount virtualAccount = new VirtualAccount("1111-11-1111111", "bank", "customer", now);
		reservation.readyForPayment(null);

		// when
		reservation.confirm(now, now, "무통장입금", virtualAccount);

		// then
		assertAll(
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_DEPOSIT),
			() -> assertThat(reservation.getVirtualAccount()).isEqualTo(virtualAccount),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING)
		);
	}

	@Test
	@DisplayName("무통장 입금이 완료되면 예약을 확정하고 티켓을 발급한다.")
	void 무통장입금_완료() {
		// given
		Reservation reservation = createReservationWithTickets();
		LocalDateTime now = LocalDateTime.now();
		VirtualAccount virtualAccount = new VirtualAccount("1111-11-1111111", "bank", "customer", now.plusDays(1));
		reservation.readyForPayment(null);
		reservation.confirm(now, null, "무통장입금", virtualAccount);

		// when
		reservation.depositReceive(now);

		// then
		assertAll(
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED),
			() -> assertThat(reservation.getPaidAt()).isEqualTo(now),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
		);
	}

	@Test
	@DisplayName("CONFIRMED 상태면 공연 시작일을 반환한다.")
	void 데드라인_CONFIRMED() {
		// given
		Reservation reservation = createReservation();
		reservation.readyForPayment(null);
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);

		// when
		LocalDateTime deadline = reservation.getDeadline();

		// then
		assertThat(deadline).isEqualTo(reservation.getShowInfo().getStartAt());
	}

	@Test
	@DisplayName("PENDING_DEPOSIT 상태면 입금 마감일을 반환한다.")
	void 데드라인_PENDING_DEPOSIT() {
		// given
		Reservation reservation = createReservation();
		VirtualAccount virtualAccount = new VirtualAccount("1111-11-1111111", "bank", "customer", LocalDateTime.now());
		reservation.readyForPayment(null);
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "무통장입금", virtualAccount);

		// when
		LocalDateTime deadline = reservation.getDeadline();

		// then
		assertThat(deadline).isEqualTo(virtualAccount.getDueDate());
	}

	@ParameterizedTest
	@DisplayName("취소 시 환불 금액은 티켓 총 금액 - 환불 수수료로 계산된다. 이때, 예매 당일일 경우 티켓 당 서비스 수수료를 더한다.")
	@CsvSource({
		"5, 10000", // 10000 - 0
		"0, 12000" // 10000 - 0 + (2000 * 1)
	})
	void 환불금액_계산_부분(int daysSinceBooked, long expectedRefundAmount) {
		// given
		Reservation reservation = createReservation();
		List<Ticket> tickets = createTickets(reservation, 2);
		ReflectionTestUtils.setField(tickets.getFirst(), "id", 1L);
		ReflectionTestUtils.setField(tickets.getLast(), "id", 2L);
		reservation.addTickets(tickets);
		reservation.readyForPayment(null);
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
		LocalDateTime canceledAt = reservation.getBookedAt().plusDays(daysSinceBooked);

		// when
		Long refundAmount = reservation.calculateRefundAmount(canceledAt, List.of(1L));

		// then
		assertThat(refundAmount).isEqualTo(expectedRefundAmount);
	}

	@Test
	@DisplayName("결제 대기 전에는 결제 완료 상태로 직접 전환할 수 없다.")
	void CREATED에서_결제확정_차단() {
		Reservation reservation = createReservationWithTickets();

		CustomException exception = assertThrows(
			CustomException.class,
			() -> reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS);
	}

	@Test
	@DisplayName("이미 확정된 카드 결제 이벤트는 티켓을 다시 발급하지 않는다.")
	void 카드결제_의미중복_noop() {
		Reservation reservation = createReservationWithTickets();
		LocalDateTime now = LocalDateTime.now();
		reservation.readyForPayment(null);
		reservation.confirm(now, now, "카드", null);

		PaymentTransitionResult result = reservation.confirm(now, now, "카드", null);

		assertAll(
			() -> assertThat(result).isEqualTo(PaymentTransitionResult.ALREADY_APPLIED),
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
		);
	}

	@Test
	@DisplayName("가상계좌 발급 이벤트가 중복되면 대기 상태와 PENDING 티켓을 유지한다.")
	void 가상계좌발급_의미중복_noop() {
		Reservation reservation = createReservationWithTickets();
		LocalDateTime now = LocalDateTime.now();
		VirtualAccount virtualAccount = new VirtualAccount(
			"1111-11-1111111", "bank", "customer", now.plusDays(1)
		);
		reservation.readyForPayment(null);
		reservation.confirm(now, null, "가상계좌", virtualAccount);

		PaymentTransitionResult result = reservation.confirm(now, null, "가상계좌", virtualAccount);

		assertAll(
			() -> assertThat(result).isEqualTo(PaymentTransitionResult.ALREADY_APPLIED),
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_DEPOSIT),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING)
		);
	}

	@Test
	@DisplayName("가상계좌 발급 전에 입금 완료 이벤트가 오면 상태를 변경하지 않는다.")
	void 가상계좌입금_순서역전_차단() {
		Reservation reservation = createReservationWithTickets();
		reservation.readyForPayment(null);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> reservation.depositReceive(LocalDateTime.now())
		);

		assertAll(
			() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS),
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING)
		);
	}

	@Test
	@DisplayName("취소된 예약은 늦은 결제 완료 이벤트가 복구하지 못한다.")
	void 취소후_늦은결제_상태복구_차단() {
		Reservation reservation = createReservationWithTickets();
		ReflectionTestUtils.setField(reservation, "status", ReservationStatus.CANCELED);

		PaymentTransitionResult result = reservation.confirm(
			LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);

		assertAll(
			() -> assertThat(result).isEqualTo(PaymentTransitionResult.TERMINAL_IGNORED),
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING)
		);
	}

	@Test
	@DisplayName("유효하지 않은 티켓 ID를 입력받으면 예외를 반환한다.")
	void 유효하지_않은_티켓_ID() {
		// given
		Reservation reservation = createReservation();
		List<Ticket> tickets = createTickets(reservation, 2);
		ReflectionTestUtils.setField(tickets.getFirst(), "id", 1L);
		ReflectionTestUtils.setField(tickets.getLast(), "id", 2L);
		reservation.addTickets(tickets);
		List<Long> ticketId = List.of(3L);

		// when & then
		assertThatThrownBy(() -> reservation.validateTicketId(ticketId))
			.isInstanceOf(CustomException.class);
	}

	@Test
	@DisplayName("같은 티켓 ID를 중복해서 취소할 수 없다.")
	void 중복_티켓_ID_취소_차단() {
		Reservation reservation = createReservationWithTickets();

		CustomException exception = assertThrows(
			CustomException.class,
			() -> reservation.validateCancelableTicketIds(List.of(1L, 1L))
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TICKET_ID);
	}

	@Test
	@DisplayName("이미 취소된 티켓을 다른 티켓과 묶어 다시 취소할 수 없다.")
	void 이미_취소된_티켓_중복_환불_차단() {
		Reservation reservation = createReservationWithTickets();
		reservation.cancel(List.of(1L), LocalDateTime.now());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> reservation.validateCancelableTicketIds(List.of(1L, 2L))
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_CANCELED_TICKET);
	}

	@ParameterizedTest
	@DisplayName("취소 또는 완료된 예약을 취소하면 예외를 반환한다.")
	@EnumSource(value = ReservationStatus.class, names = {"CANCELED", "COMPLETED"})
	void 취소_불가_상태(ReservationStatus status) {
		// given
		Reservation reservation = createReservation();
		ReflectionTestUtils.setField(reservation, "status", status);

		// when & then
		assertThatThrownBy(reservation::validateCancelStatus)
			.isInstanceOf(CustomException.class);
	}

	@ParameterizedTest
	@DisplayName("취소 티켓 수에 따라서 CANCELED, PARTIAL_CANCELED 상태로 변경된다.")
	@CsvSource({
		"1, PARTIAL_CANCELED",
		"2, CANCELED"
	})
	void 취소_상태_변경(int cancelCount, ReservationStatus expectedStatus) {
		// given
		Reservation reservation = createReservationWithTickets();
		List<Long> ticketIds = reservation.getTickets().stream().map(Ticket::getId).limit(cancelCount).toList();

		// when
		reservation.cancel(ticketIds, LocalDateTime.now());

		// then
		assertThat(reservation.getStatus()).isEqualTo(expectedStatus);
	}

	private Reservation createReservationWithTickets() {
		Reservation reservation = createReservation();
		List<Ticket> tickets = createTickets(reservation, 2);
		ReflectionTestUtils.setField(tickets.getFirst(), "id", 1L);
		ReflectionTestUtils.setField(tickets.getLast(), "id", 2L);
		reservation.addTickets(tickets);
		return reservation;
	}

	private Reservation createReservation() {
		return Reservation.create(
			UUID.randomUUID(),
			"R-001",
			"VIP석 2인",
			createShowInfo()
		);
	}

	private ShowInfo createShowInfo() {
		return ShowInfo.builder()
			.showId(1L)
			.showScheduleId(100L)
			.title("킹키부츠")
			.startAt(LocalDateTime.now().plusDays(30))
			.build();
	}

	private List<Ticket> createTickets(Reservation reservation, int count) {
		return IntStream.range(0, count)
			.mapToObj(i -> Ticket.create(reservation, "T-00" + i, "VIP", 10000L, "1층 A구역 1열 " + i + "번", (long)i))
			.toList();
	}
}

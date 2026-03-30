package org.truve.platform.ticketing.service.booking.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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

import com.truve.platform.common.exception.CustomException;

public class ReservationTest {

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
		Reservation reservation = createReservation();
		LocalDateTime now = LocalDateTime.now();

		// when
		reservation.confirm(now, now, "카드", null);

		// then
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("무통장 입금 결제를 승인하면 상태가 PENDING_DEPOSIT으로 변경되고 가상계좌 정보를 저장한다.")
	void 결제승인_무통장입금() {
		// given
		Reservation reservation = createReservation();
		LocalDateTime now = LocalDateTime.now();
		VirtualAccount virtualAccount = new VirtualAccount("1111-11-1111111", "bank", "customer", now);

		// when
		reservation.confirm(now, now, "무통장입금", virtualAccount);

		// then
		assertAll(
			() -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_DEPOSIT),
			() -> assertThat(reservation.getVirtualAccount()).isEqualTo(virtualAccount)
		);
	}

	@Test
	@DisplayName("CONFIRMED 상태면 공연 시작일을 반환한다.")
	void 데드라인_CONFIRMED() {
		// given
		Reservation reservation = createReservation();
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
		reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
		LocalDateTime canceledAt = reservation.getBookedAt().plusDays(daysSinceBooked);

		// when
		Long refundAmount = reservation.calculateRefundAmount(canceledAt, List.of(1L));

		// then
		assertThat(refundAmount).isEqualTo(expectedRefundAmount);
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

	@ParameterizedTest
	@DisplayName("취소 또는 완료된 예약을 취소하면 예외를 반환한다.")
	@EnumSource(value = ReservationStatus.class, names = {"CANCELED", "COMPLETED"})
	void 취소_불가_상태(ReservationStatus status) {
		// given
		Reservation reservation = createReservation();
		ReflectionTestUtils.setField(reservation, "status", status);

		// when & then
		assertThatThrownBy(() -> reservation.cancel(List.of(1L), LocalDateTime.now()))
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
			ShowInfo.builder()
				.showId(1L)
				.title("킹키부츠")
				.startAt(LocalDateTime.now().plusDays(30))
				.build()
		);
	}

	private List<Ticket> createTickets(Reservation reservation, int count) {
		return IntStream.range(0, count)
			.mapToObj(i -> Ticket.create(reservation, "T-00" + i, "VIP", 10000L, "1층 A구역 1열 " + i + "번"))
			.toList();
	}
}

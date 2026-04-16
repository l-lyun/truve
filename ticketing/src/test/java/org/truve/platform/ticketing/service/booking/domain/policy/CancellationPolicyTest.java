package org.truve.platform.ticketing.service.booking.domain.policy;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;

import com.truve.platform.common.exception.CustomException;

public class CancellationPolicyTest {

	@Test
	@DisplayName("공연 당일에는 취소가 불가능하다.")
	void 공연_당일_취소_불가() {
		// given
		LocalDateTime showAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime bookedAt = showAt.minusDays(10);

		Reservation reservation = createReservation(showAt, bookedAt, 1000L);

		// when & then
		assertThatThrownBy(() -> CancellationPolicy.calculate(reservation, showAt))
			.isInstanceOf(CustomException.class);
	}

	@Test
	@DisplayName("예매 당일에는 취소 수수료가 0원이다.")
	void 예매_당일_취소_수수료_없음() {
		// given
		LocalDateTime showAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime bookedAt = showAt.minusDays(5);

		Reservation reservation = createReservation(showAt, bookedAt, 1000L);

		// when
		Long cancelFee = CancellationPolicy.calculate(reservation, bookedAt);

		// then
		assertThat(cancelFee).isEqualTo(0L);
	}

	@ParameterizedTest
	@DisplayName("관람일 9일 이내 취소 수수료 퍼센트 계산 테스트")
	@CsvSource({
		"1, 300",
		"3, 200",
		"9, 100"
	})
	void 관람일_9일_이내_퍼센트_수수료_적용(int daysUntilShow, long expectedCancelFee) {
		// given
		LocalDateTime showAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime canceledAt = showAt.minusDays(daysUntilShow);
		LocalDateTime bookedAt = canceledAt.minusDays(10);

		Reservation reservation = createReservation(showAt, bookedAt, 1000L);

		// when
		Long cancelFee = CancellationPolicy.calculate(reservation, canceledAt);

		// then
		assertThat(cancelFee).isEqualTo(expectedCancelFee);
	}

	@Test
	@DisplayName("예매 후 7일 이내면서 관람일 9일 이내인 경우, 퍼센트 수수료가 계산된다.")
	void 예매_후_7일_이내_관람일_9일_이내_퍼센트_수수료_적용() {
		// given
		LocalDateTime showAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime canceledAt = showAt.minusDays(5);
		LocalDateTime bookedAt = showAt.minusDays(6);

		Reservation reservation = createReservation(showAt, bookedAt, 1000L);

		// when
		Long cancelFee = CancellationPolicy.calculate(reservation, canceledAt);

		// then
		assertThat(cancelFee).isEqualTo(100L);
	}

	@ParameterizedTest
	@DisplayName("관람일 10일 전까지는 티켓 금액의 10퍼센트랑 티켓 당 4000원 중 더 작은 금액을 적용한다.")
	@CsvSource({
		"40000, 4000",
		"1000, 100"
	})
	void 관람일_10일_이상_남은_경우_정액_또는_요율_중_작은_금액_적용(Long price, long expectedCancelFee) {
		// given
		LocalDateTime showAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime canceledAt = showAt.minusDays(11);
		LocalDateTime bookedAt = canceledAt.minusDays(10);

		Reservation reservation = createReservation(showAt, bookedAt, price);

		// when
		Long cancelFee = CancellationPolicy.calculate(reservation, canceledAt);

		// then
		assertThat(cancelFee).isEqualTo(expectedCancelFee);
	}

	private Reservation createReservation(LocalDateTime showAt, LocalDateTime bookedAt, Long price) {
		Reservation reservation = Reservation.create(
			UUID.randomUUID(),
			"R-001",
			"VIP석 2인",
			ShowInfo.builder()
				.showId(1L)
				.title("킹키부츠")
				.startAt(showAt)
				.build()
		);

		Ticket ticket1 = Ticket.create(reservation, "T-001", "VIP", price, "1층 A구역 1열 1번", 1L);
		ReflectionTestUtils.setField(ticket1, "id", 1L);

		reservation.addTickets(List.of(ticket1));

		reservation.confirm(bookedAt, bookedAt, "카드", null);

		return reservation;
	}
}

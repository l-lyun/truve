package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.ticketing.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {
	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private TicketingRedisRepository ticketingRedisRepository;

	@InjectMocks
	private SeatHoldService seatHoldService;

	@Test
	void 세션의_사용자와_회차가_일치하면_통과한다() {
		given(ticketingRedisRepository.getSessionTokenValue("session-token"))
			.willReturn(SessionTicketValueDTO.of(USER_ID, 100L));

		seatHoldService.validateSession(USER_ID, 100L, "session-token");

		verify(ticketingRedisRepository).getSessionTokenValue("session-token");
	}

	@Test
	void 좌석을_모두_소유한_경우에만_claim한다() {
		List<Long> seatIds = List.of(10L, 11L);
		given(ticketingRedisRepository.claimHoldSeats(
			org.mockito.ArgumentMatchers.eq(100L),
			org.mockito.ArgumentMatchers.eq(seatIds),
			org.mockito.ArgumentMatchers.eq("session-token"),
			org.mockito.ArgumentMatchers.anyString()
		)).willReturn(false);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> seatHoldService.claim(100L, seatIds, "session-token", "R-001")
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_BOOKING_SEAT_HOLD);
	}

	@Test
	void 예매_claim은_세션별로_구분되는_소유권_값을_사용한다() {
		List<Long> seatIds = List.of(10L, 11L);
		given(ticketingRedisRepository.claimHoldSeats(
			org.mockito.ArgumentMatchers.eq(100L),
			org.mockito.ArgumentMatchers.eq(seatIds),
			org.mockito.ArgumentMatchers.eq("session-token"),
			org.mockito.ArgumentMatchers.anyString()
		)).willReturn(true);

		seatHoldService.claim(100L, seatIds, "session-token", "R-001");

		ArgumentCaptor<String> claimValue = ArgumentCaptor.forClass(String.class);
		verify(ticketingRedisRepository).claimHoldSeats(
			org.mockito.ArgumentMatchers.eq(100L),
			org.mockito.ArgumentMatchers.eq(seatIds),
			org.mockito.ArgumentMatchers.eq("session-token"),
			claimValue.capture()
		);
		assertThat(claimValue.getValue()).startsWith("booking:session-token:R-001:");
	}

	@Test
	void DB_저장이_실패하면_claim을_기존_세션으로_복원한다() {
		List<Long> seatIds = List.of(10L, 11L);
		SeatHoldService.SeatClaim claim = new SeatHoldService.SeatClaim(
			100L, seatIds, "session-token", "claim-token"
		);
		given(ticketingRedisRepository.restoreClaimedSeats(
			100L, seatIds, "claim-token", "session-token"
		)).willReturn(true);

		boolean restored = seatHoldService.restore(claim);

		assertThat(restored).isTrue();
		verify(ticketingRedisRepository).restoreClaimedSeats(
			100L,
			seatIds,
			"claim-token",
			"session-token"
		);
	}
}

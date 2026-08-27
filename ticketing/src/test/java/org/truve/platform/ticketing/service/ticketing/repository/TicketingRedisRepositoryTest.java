package org.truve.platform.ticketing.service.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.global.support.RedisSupport;

@ExtendWith(MockitoExtension.class)
class TicketingRedisRepositoryTest {
	@Mock
	private RedisSupport redisSupport;

	@InjectMocks
	private TicketingRedisRepository ticketingRedisRepository;

	@Test
	void holdId_좌석_lease가_신규_선점_결과를_반환한다() {
		List<String> keys = List.of(
			"seat:holds:100:session-token",
			"seat:hold:meta:hold-id",
			"seat:hold:100:10",
			"seat:hold:100:11"
		);
		given(redisSupport.holdSeatLeasesWithLimit(
			keys,
			List.of(10L, 11L),
			"session-token",
			"hold-id",
			"10,11",
			"seat:hold:100:",
			"seat:hold:meta:",
			Duration.ofMinutes(10),
			Duration.ofMinutes(11),
			4
		)).willReturn(1L);

		TicketingRedisRepository.SeatHoldResult result = ticketingRedisRepository.holdSeats(
			100L, List.of(10L, 11L), "session-token", "hold-id", 4
		);

		assertThat(result).isEqualTo(TicketingRedisRepository.SeatHoldResult.NEWLY_ACQUIRED);
	}

	@Test
	void holdId_좌석_lease가_멱등_재시도_결과를_반환한다() {
		given(redisSupport.holdSeatLeasesWithLimit(
			List.of("seat:holds:100:session-token", "seat:hold:meta:hold-id", "seat:hold:100:10"),
			List.of(10L),
			"session-token",
			"hold-id",
			"10",
			"seat:hold:100:",
			"seat:hold:meta:",
			Duration.ofMinutes(10),
			Duration.ofMinutes(11),
			4
		)).willReturn(2L);

		TicketingRedisRepository.SeatHoldResult result = ticketingRedisRepository.holdSeats(
			100L, List.of(10L), "session-token", "hold-id", 4
		);

		assertThat(result).isEqualTo(TicketingRedisRepository.SeatHoldResult.ALREADY_OWNED);
	}

	@Test
	void 신규_선점_보상은_holdId와_meta를_함께_전달한다() {
		List<String> keys = List.of(
			"seat:holds:100:session-token",
			"seat:hold:meta:hold-id",
			"seat:hold:100:10",
			"seat:hold:100:11"
		);
		given(redisSupport.compensateNewlyHeldSeatLeases(
			keys, List.of(10L, 11L), "session-token", "hold-id", "10,11"
		)).willReturn(true);

		boolean compensated = ticketingRedisRepository.compensateNewlyHeldSeats(
			100L, List.of(10L, 11L), "session-token", "hold-id"
		);

		assertThat(compensated).isTrue();
		verify(redisSupport).compensateNewlyHeldSeatLeases(
			keys, List.of(10L, 11L), "session-token", "hold-id", "10,11"
		);
	}

	@Test
	void 좌석_claim은_모든_키가_세션_소유일_때만_전환한다() {
		List<String> keys = List.of(
			"seat:holds:100:session-token",
			"seat:hold:100:10",
			"seat:hold:100:11"
		);
		given(redisSupport.claimHeldSeats(keys, List.of(10L, 11L), "session-token", "claim-token"))
			.willReturn(true);

		boolean claimed = ticketingRedisRepository.claimHoldSeats(
			100L, List.of(10L, 11L), "session-token", "claim-token"
		);

		assertThat(claimed).isTrue();
		verify(redisSupport).claimHeldSeats(keys, List.of(10L, 11L), "session-token", "claim-token");
	}

	@Test
	void 좌석_claim_복원은_아직_claim_소유인_키만_기존_세션으로_되돌린다() {
		List<String> keys = List.of(
			"seat:holds:100:session-token",
			"seat:hold:100:10",
			"seat:hold:100:11"
		);
		given(redisSupport.restoreClaimedSeats(
			keys,
			List.of(10L, 11L),
			"claim-token",
			"session-token",
			Duration.ofMinutes(11)
		)).willReturn(true);

		boolean restored = ticketingRedisRepository.restoreClaimedSeats(
			100L, List.of(10L, 11L), "claim-token", "session-token"
		);

		assertThat(restored).isTrue();
		verify(redisSupport).restoreClaimedSeats(
			keys,
			List.of(10L, 11L),
			"claim-token",
			"session-token",
			Duration.ofMinutes(11)
		);
	}

	@Test
	void 사용자_회차_좌석락은_요청토큰이_일치할_때만_해제한다() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		given(redisSupport.consumeIfEquals("seat:hold:lock:100:" + userId, "request-token"))
			.willReturn(true);

		boolean released = ticketingRedisRepository.unlockSeatHold(userId, 100L, "request-token");

		assertThat(released).isTrue();
		verify(redisSupport).consumeIfEquals("seat:hold:lock:100:" + userId, "request-token");
	}
}

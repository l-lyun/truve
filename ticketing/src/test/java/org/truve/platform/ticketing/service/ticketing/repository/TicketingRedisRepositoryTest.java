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

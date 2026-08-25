package org.truve.platform.ticketing.service.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

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
		List<String> keys = List.of("seat:hold:100:10", "seat:hold:100:11");
		given(redisSupport.replaceAllIfEquals(keys, "session-token", "claim-token")).willReturn(true);

		boolean claimed = ticketingRedisRepository.claimHoldSeats(
			100L, List.of(10L, 11L), "session-token", "claim-token"
		);

		assertThat(claimed).isTrue();
		verify(redisSupport).replaceAllIfEquals(keys, "session-token", "claim-token");
	}

	@Test
	void 좌석_claim_복원은_아직_claim_소유인_키만_기존_세션으로_되돌린다() {
		List<String> keys = List.of("seat:hold:100:10", "seat:hold:100:11");
		given(redisSupport.replaceEachIfEquals(keys, "claim-token", "session-token")).willReturn(true);

		boolean restored = ticketingRedisRepository.restoreClaimedSeats(
			100L, List.of(10L, 11L), "claim-token", "session-token"
		);

		assertThat(restored).isTrue();
		verify(redisSupport).replaceEachIfEquals(keys, "claim-token", "session-token");
	}
}

package org.truve.platform.ticketing.service.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
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
	void 회차좌석키와_기존좌석키를_함께_선점한다() {
		List<String> keys = List.of("seat:hold:v2:1:10", "seat:hold:1:100");
		given(redisSupport.setAllIfAbsentOrEqual(keys, "session-token", Duration.ofMinutes(10)))
			.willReturn(true);

		boolean held = ticketingRedisRepository.tryHoldSeat(1L, 10L, 100L, "session-token");

		assertThat(held).isTrue();
		verify(redisSupport).setAllIfAbsentOrEqual(keys, "session-token", Duration.ofMinutes(10));
	}

	@Test
	void 같은세션이_소유한_회차좌석키와_기존좌석키를_함께_해제한다() {
		List<String> keys = List.of("seat:hold:v2:1:10", "seat:hold:1:100");
		given(redisSupport.deleteAllIfEqual(keys, "session-token")).willReturn(true);

		boolean deleted = ticketingRedisRepository.deleteHoldSeat(1L, 10L, 100L, "session-token");

		assertThat(deleted).isTrue();
		verify(redisSupport).deleteAllIfEqual(keys, "session-token");
	}
}

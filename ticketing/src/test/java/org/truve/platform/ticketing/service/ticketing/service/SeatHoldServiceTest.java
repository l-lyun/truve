package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}

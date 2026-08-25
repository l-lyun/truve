package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class SeatHoldLockServiceTest {
	@Mock
	private TicketingRedisRepository ticketingRedisRepository;

	@InjectMocks
	private SeatHoldLockService seatHoldLockService;

	@Test
	void 사용자_회차_락을_획득하면_요청별_토큰을_반환한다() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		given(ticketingRedisRepository.tryLockSeatHold(eq(userId), eq(100L), anyString())).willReturn(true);

		SeatHoldLockService.SeatHoldLock lock = seatHoldLockService.acquire(userId, 100L);

		assertThat(lock.lockToken()).isNotBlank();
		verify(ticketingRedisRepository).tryLockSeatHold(userId, 100L, lock.lockToken());
	}

	@Test
	void 사용자_회차_락이_이미_있으면_즉시_실패한다() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		given(ticketingRedisRepository.tryLockSeatHold(eq(userId), eq(100L), anyString())).willReturn(false);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> seatHoldLockService.acquire(userId, 100L)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEAT_HOLD_IN_PROGRESS);
	}
}

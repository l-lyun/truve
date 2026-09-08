package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedApplyException.FailureReason;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedFailureService.FailureRecordResult;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedTransactionService.ApplyResult;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedTransactionService.RecoveryResult;

@ExtendWith(MockitoExtension.class)
class HoldRequestedEventHandlerTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 0);
	private static final Clock CLOCK = Clock.fixed(
		Instant.parse("2026-08-27T08:00:00Z"), ZoneId.of("Asia/Seoul"));

	@Mock
	private TicketingRedisRepository redisRepository;
	@Mock
	private HoldRequestedTransactionService transactionService;
	@Mock
	private HoldRequestedFailureService failureService;

	private HoldRequestedEventHandler handler;

	@BeforeEach
	void setUp() {
		handler = new HoldRequestedEventHandler(redisRepository, transactionService, failureService, CLOCK);
	}

	@Test
	void 유효한_lease의_이벤트는_성공_트랜잭션으로_처리한다() {
		TicketingEventCommand.HoldRequested event = event(NOW.plusMinutes(10));
		given(redisRepository.ownsHeldSeats(100L, List.of(10L, 11L), "session-token", "H-001"))
			.willReturn(true);
		given(transactionService.apply(event)).willReturn(ApplyResult.APPLIED);

		handler.handle(event);

		verify(transactionService).apply(event);
		verifyNoInteractions(failureService);
		verify(redisRepository, never()).compensateNewlyHeldSeats(100L, List.of(10L, 11L), "session-token", "H-001");
	}

	@Test
	void expiresAt과_현재시각이_같으면_EXPIRED를_저장한_뒤_Redis를_보상한다() {
		TicketingEventCommand.HoldRequested event = event(NOW);
		given(failureService.record(event, FailureReason.EXPIRED)).willReturn(FailureRecordResult.RECORDED);

		handler.handle(event);

		verify(failureService).record(event, FailureReason.EXPIRED);
		verify(redisRepository).compensateNewlyHeldSeats(
			100L, List.of(10L, 11L), "session-token", "H-001");
		verify(transactionService, never()).apply(event);
	}

	@Test
	void Redis_소유권이_없으면_HOLD_FAILED를_저장한_뒤_보상한다() {
		TicketingEventCommand.HoldRequested event = event(NOW.plusMinutes(10));
		given(failureService.record(event, FailureReason.SEAT_CONFLICT)).willReturn(FailureRecordResult.RECORDED);

		handler.handle(event);

		verify(failureService).record(event, FailureReason.SEAT_CONFLICT);
		verify(redisRepository).compensateNewlyHeldSeats(
			100L, List.of(10L, 11L), "session-token", "H-001");
	}

	@Test
	void DB_일시오류_후_상태가_미확정이면_보상하지_않고_예외를_전파한다() {
		TicketingEventCommand.HoldRequested event = event(NOW.plusMinutes(10));
		RuntimeException failure = new RuntimeException("db unavailable");
		given(redisRepository.ownsHeldSeats(100L, List.of(10L, 11L), "session-token", "H-001"))
			.willReturn(true);
		given(transactionService.apply(event)).willThrow(failure);
		given(transactionService.resolveAfterFailure(event)).willReturn(RecoveryResult.RETRY_REQUIRED);

		assertThatThrownBy(() -> handler.handle(event)).isSameAs(failure);

		verifyNoInteractions(failureService);
		verify(redisRepository, never()).compensateNewlyHeldSeats(100L, List.of(10L, 11L), "session-token", "H-001");
	}

	@Test
	void 낙관적락_충돌_후_다른_소유자가_확인되면_HOLD_FAILED와_보상을_수행한다() {
		TicketingEventCommand.HoldRequested event = event(NOW.plusMinutes(10));
		OptimisticLockingFailureException conflict = new OptimisticLockingFailureException("conflict");
		given(redisRepository.ownsHeldSeats(100L, List.of(10L, 11L), "session-token", "H-001"))
			.willReturn(true);
		given(transactionService.apply(event)).willThrow(conflict);
		given(transactionService.resolveAfterFailure(event)).willReturn(RecoveryResult.CONFLICT);
		given(failureService.record(event, FailureReason.SEAT_CONFLICT)).willReturn(FailureRecordResult.RECORDED);

		handler.handle(event);

		verify(failureService).record(event, FailureReason.SEAT_CONFLICT);
		verify(redisRepository).compensateNewlyHeldSeats(
			100L, List.of(10L, 11L), "session-token", "H-001");
	}

	@Test
	void 잘못된_이벤트는_DB와_Redis를_변경하지_않고_거절한다() {
		TicketingEventCommand.HoldRequested malformed = TicketingEventCommand.HoldRequested.of(
			"H-001", "R-001", UUID.randomUUID(), "session-token", 100L,
			List.of(1L, 2L, 3L, 4L, 5L), NOW.plusMinutes(10));

		assertThatThrownBy(() -> handler.handle(malformed))
			.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(redisRepository, transactionService, failureService);
	}

	private TicketingEventCommand.HoldRequested event(LocalDateTime expiresAt) {
		return TicketingEventCommand.HoldRequested.of(
			"H-001", "R-001",
			UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
			"session-token", 100L, List.of(10L, 11L), expiresAt);
	}
}

package org.truve.platform.ticketing.service.ticketing.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedApplyException.FailureReason;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedFailureService.FailureRecordResult;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedTransactionService.ApplyResult;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedTransactionService.RecoveryResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HoldRequestedEventHandler {
	private final TicketingRedisRepository ticketingRedisRepository;
	private final HoldRequestedTransactionService transactionService;
	private final HoldRequestedFailureService failureService;
	private final Clock clock;

	public void handle(TicketingEventCommand.HoldRequested event) {
		validateEventShape(event);

		if (!LocalDateTime.now(clock).isBefore(event.getExpiresAt())) {
			recordTerminalAndCompensate(event, FailureReason.EXPIRED);
			return;
		}
		if (!ticketingRedisRepository.ownsHeldSeats(
			event.getShowScheduleId(),
			event.getScheduledSeatIds(),
			event.getSessionToken(),
			event.getHoldId()
		)) {
			recordTerminalAndCompensate(event, FailureReason.SEAT_CONFLICT);
			return;
		}

		try {
			ApplyResult result = transactionService.apply(event);
			if (result == ApplyResult.TERMINAL_IGNORED) {
				compensateSafely(event);
			}
		} catch (HoldRequestedApplyException exception) {
			recordTerminalAndCompensate(event, exception.getReason());
		} catch (OptimisticLockingFailureException exception) {
			RecoveryResult recoveryResult = resolveAfterFailure(event, exception);
			if (recoveryResult == RecoveryResult.CONFLICT) {
				recordTerminalAndCompensate(event, FailureReason.SEAT_CONFLICT);
				return;
			}
			if (recoveryResult == RecoveryResult.RETRY_REQUIRED) {
				throw exception;
			}
		} catch (RuntimeException exception) {
			RecoveryResult recoveryResult = resolveAfterFailure(event, exception);
			if (recoveryResult == RecoveryResult.APPLIED
				|| recoveryResult == RecoveryResult.TERMINAL) {
				return;
			}
			// 일시적인 DB/직렬화 장애나 커밋 불확실 상태에서는 lease를 유지하고 Kafka 재시도에 맡긴다.
			throw exception;
		}
	}

	private RecoveryResult resolveAfterFailure(
		TicketingEventCommand.HoldRequested event,
		RuntimeException originalException
	) {
		try {
			return transactionService.resolveAfterFailure(event);
		} catch (RuntimeException lookupException) {
			originalException.addSuppressed(lookupException);
			log.warn("HOLD_REQUESTED 커밋 결과를 확인할 수 없어 Redis lease를 유지합니다. holdId={}",
				event.getHoldId(), lookupException);
			throw originalException;
		}
	}

	private void recordTerminalAndCompensate(
		TicketingEventCommand.HoldRequested event,
		FailureReason reason
	) {
		FailureRecordResult result = failureService.record(event, reason);
		if (result != FailureRecordResult.COMPLETED_IGNORED) {
			compensateSafely(event);
		}
	}

	private void compensateSafely(TicketingEventCommand.HoldRequested event) {
		try {
			if (!ticketingRedisRepository.compensateNewlyHeldSeats(
				event.getShowScheduleId(),
				event.getScheduledSeatIds(),
				event.getSessionToken(),
				event.getHoldId()
			)) {
				log.debug("보상할 Redis 좌석 lease가 없거나 소유권이 변경되었습니다. holdId={}", event.getHoldId());
			}
		} catch (RuntimeException exception) {
			// DB terminal 상태가 확정됐으므로 Redis는 TTL로 수렴시킨다.
			log.warn("HOLD_REQUESTED Redis 보상에 실패했습니다. holdId={}", event.getHoldId(), exception);
		}
	}

	private void validateEventShape(TicketingEventCommand.HoldRequested event) {
		Objects.requireNonNull(event, "hold requested event must not be null");
		List<Long> seatIds = Objects.requireNonNull(event.getScheduledSeatIds(), "scheduledSeatIds must not be null");
		if (event.getHoldId() == null || event.getHoldId().isBlank()
			|| event.getReservationNumber() == null || event.getReservationNumber().isBlank()
			|| event.getSessionToken() == null || event.getSessionToken().isBlank()
			|| event.getUserId() == null
			|| event.getShowScheduleId() == null
			|| event.getExpiresAt() == null
			|| seatIds.isEmpty()
			|| seatIds.size() > 4
			|| seatIds.stream().anyMatch(Objects::isNull)
			|| new HashSet<>(seatIds).size() != seatIds.size()) {
			throw new IllegalArgumentException("invalid HOLD_REQUESTED event");
		}
	}
}

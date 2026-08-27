package org.truve.platform.ticketing.service.ticketing.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationCommand;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationResult;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository.SeatHoldResult;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldSagaService {
	private static final int MAX_HOLD_SEAT_COUNT = 4;
	private static final long HOLD_MINUTES = 10L;

	private final TicketingService ticketingService;
	private final TicketingSecurityService ticketingSecurityService;
	private final SeatHoldLockService seatHoldLockService;
	private final TicketingRedisRepository ticketingRedisRepository;
	private final ScheduledSeatRepository scheduledSeatRepository;
	private final ShowScheduledRepository showScheduledRepository;
	private final HoldReservationCreationService holdReservationCreationService;

	public TicketingResponse.HoldAccepted hold(
		Long showScheduleId,
		UUID userId,
		String sessionToken,
		String idempotencyKey,
		List<Long> scheduledSeatIds
	) {
		validateRequest(showScheduleId, idempotencyKey, scheduledSeatIds);
		ticketingSecurityService.findMacro(sessionToken);
		ticketingService.heartbeat(showScheduleId, userId, sessionToken);
		List<Long> sortedSeatIds = scheduledSeatIds.stream().sorted().toList();
		String holdId = NumberGenerator.generateHoldId(userId, showScheduleId, idempotencyKey);
		String holdRequestFingerprint = NumberGenerator.generateHoldRequestFingerprint(sortedSeatIds);

		SeatHoldLockService.SeatHoldLock lock = seatHoldLockService.acquire(userId, showScheduleId);
		try {
			Optional<HoldReservationResult> existing = holdReservationCreationService.findExisting(
				holdId, holdRequestFingerprint, userId, showScheduleId);
			if (existing.isPresent()) {
				return toResponse(existing.get());
			}

			validateSeats(showScheduleId, sortedSeatIds);
			LocalDateTime requestedAt = LocalDateTime.now();
			SeatHoldResult redisResult = ticketingRedisRepository.holdSeats(
				showScheduleId,
				sortedSeatIds,
				sessionToken,
				holdId,
				MAX_HOLD_SEAT_COUNT
			);
			validateRedisResult(redisResult);
			LocalDateTime expiresAt = resolveExpiresAt(redisResult, holdId, requestedAt);

			try {
				HoldReservationResult created = holdReservationCreationService.create(new HoldReservationCommand(
					holdId, holdRequestFingerprint, userId, sessionToken,
					showScheduleId, sortedSeatIds, expiresAt));
				return toResponse(created);
			} catch (RuntimeException exception) {
				Optional<HoldReservationResult> committed;
				try {
					committed = holdReservationCreationService.findExisting(
						holdId, holdRequestFingerprint, userId, showScheduleId);
				} catch (RuntimeException lookupException) {
					exception.addSuppressed(lookupException);
					log.warn("좌석 HOLD 주문 커밋 여부를 확인할 수 없어 Redis lease를 유지합니다. holdId={}",
						holdId, lookupException);
					throw exception;
				}
				if (committed.isPresent()) {
					return toResponse(committed.get());
				}
				if (redisResult == SeatHoldResult.NEWLY_ACQUIRED) {
					compensateSafely(showScheduleId, sortedSeatIds, sessionToken, holdId, exception);
				}
				if (exception instanceof DataIntegrityViolationException) {
					throw new CustomException(ErrorCode.ALREADY_BOOKED_SHOW);
				}
				throw exception;
			}
		} finally {
			releaseLockSafely(lock);
		}
	}

	private void validateRequest(Long showScheduleId, String idempotencyKey, List<Long> scheduledSeatIds) {
		Preconditions.validate(showScheduleId != null, ErrorCode.INVALID_SHOW_SCHEDULE);
		Preconditions.validate(idempotencyKey != null && !idempotencyKey.isBlank(), ErrorCode.INVALID_BOOKING_SEAT_HOLD);
		Preconditions.validate(scheduledSeatIds != null && !scheduledSeatIds.isEmpty(), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(scheduledSeatIds.size() <= MAX_HOLD_SEAT_COUNT, ErrorCode.EXCEEDED_MAX_TICKET_COUNT);
		Preconditions.validate(scheduledSeatIds.stream().noneMatch(Objects::isNull), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(new HashSet<>(scheduledSeatIds).size() == scheduledSeatIds.size(), ErrorCode.NOT_CORRECT_SEAT);
	}

	private void validateSeats(Long showScheduleId, List<Long> sortedSeatIds) {
		Preconditions.validate(showScheduledRepository.existsById(showScheduleId), ErrorCode.INVALID_SHOW_SCHEDULE);
		List<ScheduledSeat> seats = scheduledSeatRepository.findAllById(sortedSeatIds);
		Preconditions.validate(seats.size() == sortedSeatIds.size(), ErrorCode.NOT_CORRECT_SEAT);
		Preconditions.validate(
			seats.stream().allMatch(seat -> showScheduleId.equals(seat.getShowScheduleId())),
			ErrorCode.NOT_CORRECT_SEAT
		);
		Preconditions.validate(seats.stream().allMatch(ScheduledSeat::isAvailable), ErrorCode.ALREADY_SOLD_SEAT);
	}

	private void validateRedisResult(SeatHoldResult result) {
		Preconditions.validate(result != SeatHoldResult.LIMIT_EXCEEDED, ErrorCode.EXCEEDED_MAX_TICKET_COUNT);
		Preconditions.validate(
			result == SeatHoldResult.NEWLY_ACQUIRED || result == SeatHoldResult.ALREADY_OWNED,
			ErrorCode.ALREADY_HOLD_SEAT
		);
	}

	private LocalDateTime resolveExpiresAt(
		SeatHoldResult redisResult,
		String holdId,
		LocalDateTime requestedAt
	) {
		if (redisResult == SeatHoldResult.NEWLY_ACQUIRED) {
			return requestedAt.plusMinutes(HOLD_MINUTES);
		}
		long remainingTtlMillis = ticketingRedisRepository.getHoldTtlMillis(holdId);
		Preconditions.validate(remainingTtlMillis > 0, ErrorCode.INVALID_BOOKING_SEAT_HOLD);
		return LocalDateTime.now().plus(Duration.ofMillis(remainingTtlMillis));
	}

	private void compensateSafely(
		Long showScheduleId,
		List<Long> seatIds,
		String sessionToken,
		String holdId,
		RuntimeException originalException
	) {
		try {
			if (!ticketingRedisRepository.compensateNewlyHeldSeats(
				showScheduleId, seatIds, sessionToken, holdId)) {
				log.warn("좌석 HOLD 보상 대상이 없거나 소유권이 변경되었습니다. holdId={}", holdId);
			}
		} catch (RuntimeException compensationException) {
			originalException.addSuppressed(compensationException);
			log.warn("좌석 HOLD 보상에 실패했습니다. holdId={}", holdId, compensationException);
		}
	}

	private void releaseLockSafely(SeatHoldLockService.SeatHoldLock lock) {
		try {
			if (!seatHoldLockService.release(lock)) {
				log.warn("좌석 HOLD 요청 락이 만료됐거나 소유권이 변경되었습니다. lockToken={}", lock.lockToken());
			}
		} catch (RuntimeException exception) {
			log.warn("좌석 HOLD 요청 락 해제에 실패했습니다. lockToken={}", lock.lockToken(), exception);
		}
	}

	private TicketingResponse.HoldAccepted toResponse(HoldReservationResult result) {
		return new TicketingResponse.HoldAccepted(
			result.reservationNumber(), result.status(), result.expiresAt());
	}
}

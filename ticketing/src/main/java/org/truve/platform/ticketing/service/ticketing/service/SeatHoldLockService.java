package org.truve.platform.ticketing.service.ticketing.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatHoldLockService {
	private final TicketingRedisRepository ticketingRedisRepository;

	public SeatHoldLock acquire(UUID userId, Long showScheduleId) {
		String lockToken = UUID.randomUUID().toString();
		boolean locked = ticketingRedisRepository.tryLockSeatHold(userId, showScheduleId, lockToken);
		Preconditions.validate(locked, ErrorCode.SEAT_HOLD_IN_PROGRESS);
		return new SeatHoldLock(userId, showScheduleId, lockToken);
	}

	public boolean release(SeatHoldLock lock) {
		return ticketingRedisRepository.unlockSeatHold(
			lock.userId(),
			lock.showScheduleId(),
			lock.lockToken()
		);
	}

	public record SeatHoldLock(UUID userId, Long showScheduleId, String lockToken) {
	}
}

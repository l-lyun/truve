package org.truve.platform.ticketing.service.booking.repository;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.truve.platform.ticketing.service.global.support.RedisSupport;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BookingRedisRepository {
	private static final String BOOKING_LOCK_PREFIX = "booking:lock:";
	private static final Duration BOOKING_LOCK_TTL = Duration.ofSeconds(10);

	private final RedisSupport redisSupport;

	public boolean tryLock(UUID userId, Long showScheduleId, String lockToken) {
		return redisSupport.setIfAbsent(bookingLockKey(userId, showScheduleId), lockToken, BOOKING_LOCK_TTL);
	}

	public boolean unlock(UUID userId, Long showScheduleId, String lockToken) {
		return redisSupport.consumeIfEquals(bookingLockKey(userId, showScheduleId), lockToken);
	}

	private String bookingLockKey(UUID userId, Long showScheduleId) {
		return BOOKING_LOCK_PREFIX + userId + ":" + showScheduleId;
	}
}

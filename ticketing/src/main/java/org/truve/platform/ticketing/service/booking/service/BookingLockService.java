package org.truve.platform.ticketing.service.booking.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.booking.repository.BookingRedisRepository;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingLockService {
	private final BookingRedisRepository bookingRedisRepository;

	public BookingLock acquire(UUID userId, Long showScheduleId) {
		String lockToken = UUID.randomUUID().toString();
		boolean locked = bookingRedisRepository.tryLock(userId, showScheduleId, lockToken);
		Preconditions.validate(locked, ErrorCode.BOOKING_IN_PROGRESS);
		return new BookingLock(userId, showScheduleId, lockToken);
	}

	public void release(BookingLock bookingLock) {
		bookingRedisRepository.unlock(
			bookingLock.userId(),
			bookingLock.showScheduleId(),
			bookingLock.lockToken()
		);
	}

	public record BookingLock(UUID userId, Long showScheduleId, String lockToken) {
	}
}

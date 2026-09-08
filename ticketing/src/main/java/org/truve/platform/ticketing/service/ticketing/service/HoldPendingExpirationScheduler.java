package org.truve.platform.ticketing.service.ticketing.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(prefix = "ticketing.hold", name = "expiration-enabled", havingValue = "true",
	matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class HoldPendingExpirationScheduler {
	private final ReservationRepository reservationRepository;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${ticketing.hold.expiration-scan-delay:30s}")
	@Transactional
	public void expirePendingHolds() {
		int expiredCount = reservationRepository.expirePendingHolds(
			LocalDateTime.now(clock),
			ReservationStatus.HOLD_PENDING,
			ReservationStatus.EXPIRED
		);
		if (expiredCount > 0) {
			log.info("만료된 HOLD_PENDING 주문을 정리했습니다. count={}", expiredCount);
		}
	}
}

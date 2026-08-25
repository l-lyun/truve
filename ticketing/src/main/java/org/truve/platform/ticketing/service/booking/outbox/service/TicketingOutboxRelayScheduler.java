package org.truve.platform.ticketing.service.booking.outbox.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingOutboxRelayScheduler {
	private static final int RELAY_BATCH_SIZE = 100;

	private final TicketingOutboxEventRepository outboxRepository;
	private final TicketingOutboxClaimService claimService;
	private final TicketingOutboxMessageRelay messageRelay;

	@Value("${ticketing.outbox.claim-timeout-ms:300000}")
	private long claimTimeoutMs;

	@Scheduled(fixedDelayString = "${ticketing.outbox.relay.fixed-delay-ms:3000}")
	public void relay() {
		List<ClaimedOutboxEvent> claimedEvents = claimService.claimBatch(RELAY_BATCH_SIZE);
		if (!claimedEvents.isEmpty()) {
			List<OutboxRelayResult> results = messageRelay.relay(claimedEvents);
			claimService.complete(results);
		}
	}

	@Scheduled(fixedDelayString = "${ticketing.outbox.claim-recovery-delay-ms:30000}")
	public void recoverExpiredClaims() {
		LocalDateTime expiredBefore = LocalDateTime.now().minus(Duration.ofMillis(claimTimeoutMs));
		claimService.recoverExpiredClaims(expiredBefore);
	}

	@Scheduled(cron = "${ticketing.outbox.cleanup.cron:0 0 3 * * *}")
	@Transactional
	public void deletePublished() {
		outboxRepository.deleteByStatus(OutboxStatus.PUBLISHED);
	}
}

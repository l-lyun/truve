package org.truve.platform.ticketing.service.booking.outbox.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketingOutboxClaimService {
	private final TicketingOutboxEventRepository outboxRepository;
	private Counter publishedCounter;
	private Counter failedCounter;
	private Counter staleCounter;
	private Counter recoveredCounter;

	@Autowired(required = false)
	void configureMetrics(MeterRegistry registry) {
		publishedCounter = registry.counter("ticketing.outbox.completion", "outcome", "published");
		failedCounter = registry.counter("ticketing.outbox.completion", "outcome", "failed");
		staleCounter = registry.counter("ticketing.outbox.completion", "outcome", "stale");
		recoveredCounter = registry.counter("ticketing.outbox.claims.recovered");
	}

	@Transactional
	public List<ClaimedOutboxEvent> claimBatch(int batchSize) {
		UUID claimToken = UUID.randomUUID();
		LocalDateTime claimedAt = LocalDateTime.now();
		List<TicketingOutboxEvent> pending = outboxRepository.findClaimableHeadsForUpdate(
			OutboxStatus.PENDING.name(), batchSize
		);
		pending.forEach(event -> event.claim(claimToken, claimedAt));
		List<TicketingOutboxEvent> failed = outboxRepository.findClaimableHeadsForUpdate(
			OutboxStatus.FAILED.name(), batchSize
		);
		failed.forEach(event -> event.claim(claimToken, claimedAt));
		List<TicketingOutboxEvent> events = java.util.stream.Stream.concat(pending.stream(), failed.stream()).toList();
		return events.stream().map(ClaimedOutboxEvent::from).toList();
	}

	@Transactional
	public void complete(List<OutboxRelayResult> results) {
		int published = 0;
		int failed = 0;
		int stale = 0;
		for (OutboxRelayResult result : results) {
			int updated = result.published()
				? outboxRepository.markPublishedIfOwned(
					result.id(), result.claimToken(), OutboxStatus.PROCESSING, OutboxStatus.PUBLISHED
				)
				: outboxRepository.markFailedIfOwned(
					result.id(), result.claimToken(), OutboxStatus.PROCESSING, OutboxStatus.FAILED
				);
			if (updated == 0) {
				stale++;
				log.warn("Outbox claim 소유권이 만료되어 처리 결과를 반영하지 않습니다. id={}, claimToken={}",
					result.id(), result.claimToken());
			} else if (result.published()) {
				published += updated;
			} else {
				failed += updated;
			}
		}
		recordAfterCommit(publishedCounter, published);
		recordAfterCommit(failedCounter, failed);
		recordAfterCommit(staleCounter, stale);
	}

	@Transactional
	public int recoverExpiredClaims(LocalDateTime expiredBefore) {
		int recovered = outboxRepository.recoverExpiredClaims(
			expiredBefore, OutboxStatus.PROCESSING, OutboxStatus.FAILED
		);
		recordAfterCommit(recoveredCounter, recovered);
		return recovered;
	}

	private void recordAfterCommit(Counter counter, int count) {
		if (counter == null || count == 0 || !TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				counter.increment(count);
			}
		});
	}
}

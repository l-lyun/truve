package org.truve.platform.ticketing.service.booking.outbox.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketingOutboxClaimService {
	private final TicketingOutboxEventRepository outboxRepository;

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
		for (OutboxRelayResult result : results) {
			int updated = result.published()
				? outboxRepository.markPublishedIfOwned(
					result.id(), result.claimToken(), OutboxStatus.PROCESSING, OutboxStatus.PUBLISHED
				)
				: outboxRepository.markFailedIfOwned(
					result.id(), result.claimToken(), OutboxStatus.PROCESSING, OutboxStatus.FAILED
				);
			if (updated == 0) {
				log.warn("Outbox claim 소유권이 만료되어 처리 결과를 반영하지 않습니다. id={}, claimToken={}",
					result.id(), result.claimToken());
			}
		}
	}

	@Transactional
	public int recoverExpiredClaims(LocalDateTime expiredBefore) {
		return outboxRepository.recoverExpiredClaims(
			expiredBefore, OutboxStatus.PROCESSING, OutboxStatus.FAILED
		);
	}
}

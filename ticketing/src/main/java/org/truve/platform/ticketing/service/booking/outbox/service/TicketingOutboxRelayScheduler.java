package org.truve.platform.ticketing.service.booking.outbox.service;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxRelayExecutor;
import com.truve.platform.common.outbox.OutboxStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingOutboxRelayScheduler {
	private static final int RELAY_BATCH_SIZE = 100;
	private static final List<OutboxStatus> ACTIVE_STATUSES =
		List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

	private final TicketingOutboxEventRepository outboxRepository;
	private final OutboxRelayExecutor outboxRelayExecutor;

	@Scheduled(fixedDelayString = "${ticketing.outbox.relay.fixed-delay-ms:3000}")
	public void relay() {
		PageRequest batch = PageRequest.of(0, RELAY_BATCH_SIZE);
		List<TicketingOutboxEvent> pending = outboxRepository.findRelayHeads(
			OutboxStatus.PENDING, ACTIVE_STATUSES, batch
		);
		List<TicketingOutboxEvent> failed = outboxRepository.findRelayHeads(
			OutboxStatus.FAILED, ACTIVE_STATUSES, batch
		);
		List<TicketingOutboxEvent> relayTargets = Stream.concat(pending.stream(), failed.stream())
			.toList();
		if (!relayTargets.isEmpty()) {
			outboxRelayExecutor.execute(relayTargets);
			outboxRepository.saveAll(relayTargets);
		}
	}

	@Scheduled(cron = "${ticketing.outbox.cleanup.cron:0 0 3 * * *}")
	@Transactional
	public void deletePublished() {
		outboxRepository.deleteByStatus(OutboxStatus.PUBLISHED);
	}
}

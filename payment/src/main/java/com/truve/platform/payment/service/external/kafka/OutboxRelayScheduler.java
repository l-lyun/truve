package com.truve.platform.payment.service.external.kafka;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.outbox.OutboxRelayExecutor;
import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;
import com.truve.platform.payment.service.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {
	private static final int RELAY_BATCH_SIZE = 100;
	private static final List<OutboxStatus> ACTIVE_STATUSES =
		List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

	private final PaymentOutboxEventRepository outboxRepository;
	private final OutboxRelayExecutor outboxRelayExecutor;

	@Scheduled(fixedDelay = 3000)
	@Transactional
	public void relay() {
		PageRequest batch = PageRequest.of(0, RELAY_BATCH_SIZE);
		List<PaymentOutboxEvent> pending = outboxRepository.findRelayHeads(
			OutboxStatus.PENDING, ACTIVE_STATUSES, batch
		);
		List<PaymentOutboxEvent> failed = outboxRepository.findRelayHeads(
			OutboxStatus.FAILED, ACTIVE_STATUSES, batch
		);
		List<PaymentOutboxEvent> relayTargets = Stream.concat(pending.stream(), failed.stream())
			.toList();
		if (!relayTargets.isEmpty()) {
			outboxRelayExecutor.execute(relayTargets);
		}
	}

	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public void deletePublished() {
		outboxRepository.deleteByStatus(OutboxStatus.PUBLISHED);
	}
}

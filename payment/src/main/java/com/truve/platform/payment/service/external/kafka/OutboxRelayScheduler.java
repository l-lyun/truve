package com.truve.platform.payment.service.external.kafka;

import java.util.List;

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

	private final PaymentOutboxEventRepository outboxRepository;
	private final OutboxRelayExecutor outboxRelayExecutor;

	@Scheduled(fixedDelay = 3000)
	@Transactional
	public void relay() {
		List<PaymentOutboxEvent> pending = outboxRepository.findByStatus(OutboxStatus.PENDING);
		if (!pending.isEmpty()) {
			outboxRelayExecutor.execute(pending);
		}
	}

	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public void deletePublished() {
		outboxRepository.deleteByStatus(OutboxStatus.PUBLISHED);
	}
}

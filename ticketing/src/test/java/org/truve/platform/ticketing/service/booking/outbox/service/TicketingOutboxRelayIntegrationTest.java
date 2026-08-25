package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

@DataJpaTest
@Import({
	TicketingOutboxRelayScheduler.class,
	TicketingOutboxClaimService.class,
	TicketingOutboxMessageRelay.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketingOutboxRelayIntegrationTest {

	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private TicketingOutboxRelayScheduler scheduler;
	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
		outboxRepository.saveAndFlush(TicketingOutboxEvent.create(
			"booking.ticketing", "R-001", "{}", "SOLD_CONFIRMED"
		));
	}

	@Test
	void Kafka_실패상태를_DB에_저장하고_다음_Relay에서_성공상태로_전환한다() {
		given(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
			.willAnswer(invocation -> {
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				return CompletableFuture.failedFuture(new IllegalStateException("kafka failure"));
			});

		scheduler.relay();

		TicketingOutboxEvent failed = outboxRepository.findAll().getFirst();
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(failed.getRetryCount()).isEqualTo(1);

		given(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
			.willAnswer(invocation -> {
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				return CompletableFuture.completedFuture(null);
			});

		scheduler.relay();

		TicketingOutboxEvent published = outboxRepository.findAll().getFirst();
		assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(published.getRetryCount()).isEqualTo(1);
	}
}

package com.truve.platform.payment.service.external.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.truve.platform.common.outbox.OutboxRelayExecutor;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;

@SuppressWarnings("unchecked")
class OutboxRelayExecutorTest {

	@Test
	void outbox의_고정_eventId를_Kafka_헤더로_전달한다() {
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		given(kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(CompletableFuture.completedFuture(null));
		OutboxRelayExecutor executor = new OutboxRelayExecutor(kafkaTemplate);
		PaymentOutboxEvent event = PaymentOutboxEvent.create(
			"payment.booking", "R-001", "{}", "CONFIRMED"
		);

		executor.execute(List.of(event));

		ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(recordCaptor.capture());
		ProducerRecord<String, String> record = recordCaptor.getValue();
		assertThat(event.getEventId()).isNotNull();
		assertThat(new String(record.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8))
			.isEqualTo(event.getEventId().toString());
		assertThat(new String(record.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
			.isEqualTo("CONFIRMED");
		assertThat(record.key()).isEqualTo("R-001");
	}

	@Test
	void 같은_예약의_선행_이벤트가_실패하면_후속_이벤트를_건너뛰고_다른_예약은_발행한다() {
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("kafka unavailable"));
		given(kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(failed)
			.willReturn(CompletableFuture.completedFuture(null));
		OutboxRelayExecutor executor = new OutboxRelayExecutor(kafkaTemplate);
		PaymentOutboxEvent first = PaymentOutboxEvent.create(
			"payment.booking", "R-001", "{}", "CONFIRMED"
		);
		PaymentOutboxEvent sameReservationNext = PaymentOutboxEvent.create(
			"payment.booking", "R-001", "{}", "DEPOSIT_RECEIVED"
		);
		PaymentOutboxEvent otherReservation = PaymentOutboxEvent.create(
			"payment.booking", "R-002", "{}", "CONFIRMED"
		);

		executor.execute(List.of(first, sameReservationNext, otherReservation));

		ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate, times(2)).send(recordCaptor.capture());
		assertThat(recordCaptor.getAllValues()).extracting(ProducerRecord::key)
			.containsExactly("R-001", "R-002");
		assertThat(first.getRetryCount()).isEqualTo(1);
		assertThat(sameReservationNext.getRetryCount()).isZero();
	}
}

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
import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;

@SuppressWarnings("unchecked")
class OutboxRelayExecutorTest {

	@Test
	void 선행_이벤트가_실패하면_같은_예약의_후속_이벤트만_건너뛴다() {
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("kafka unavailable"));
		given(kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(failed)
			.willReturn(CompletableFuture.completedFuture(null));
		OutboxRelayExecutor executor = new OutboxRelayExecutor(kafkaTemplate);
		PaymentOutboxEvent failedConfirmed = event("R-001", "CONFIRMED");
		PaymentOutboxEvent sameReservationDeposit = event("R-001", "DEPOSIT_RECEIVED");
		PaymentOutboxEvent otherReservationConfirmed = event("R-002", "CONFIRMED");
		PaymentOutboxEvent otherTopicSameKey = PaymentOutboxEvent.create(
			"payment.membership", "R-001", "{}", "CONFIRMED"
		);

		executor.execute(List.of(
			failedConfirmed, sameReservationDeposit, otherReservationConfirmed, otherTopicSameKey
		));

		ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate, times(3)).send(recordCaptor.capture());
		assertThat(recordCaptor.getAllValues())
			.extracting(record -> record.topic() + ":" + record.key())
			.containsExactly("payment.booking:R-001", "payment.booking:R-002", "payment.membership:R-001");
		assertThat(failedConfirmed.getRetryCount()).isEqualTo(1);
		assertThat(sameReservationDeposit.getRetryCount()).isZero();
	}

	@Test
	void 선행_실패_이벤트의_재시도가_성공하면_같은_예약의_후속_이벤트를_순서대로_발행한다() {
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		given(kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(CompletableFuture.completedFuture(null));
		OutboxRelayExecutor executor = new OutboxRelayExecutor(kafkaTemplate);
		PaymentOutboxEvent failedConfirmed = event("R-001", "CONFIRMED");
		failedConfirmed.markFailed();
		PaymentOutboxEvent pendingDeposit = event("R-001", "DEPOSIT_RECEIVED");

		executor.execute(List.of(failedConfirmed, pendingDeposit));

		ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate, times(2)).send(recordCaptor.capture());
		assertThat(recordCaptor.getAllValues())
			.extracting(record -> new String(
				record.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
			.containsExactly("CONFIRMED", "DEPOSIT_RECEIVED");
		assertThat(failedConfirmed.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(pendingDeposit.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
	}

	private PaymentOutboxEvent event(String reservationNumber, String eventType) {
		return PaymentOutboxEvent.create("payment.booking", reservationNumber, "{}", eventType);
	}
}

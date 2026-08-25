package com.truve.platform.payment.service.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import com.truve.platform.common.outbox.OutboxRelayExecutor;
import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;

@DataJpaTest
@SuppressWarnings("unchecked")
class PaymentOutboxEventRepositoryTest {
	private static final List<OutboxStatus> ACTIVE_STATUSES =
		List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

	@Autowired
	private PaymentOutboxEventRepository outboxRepository;

	@Test
	void 실패한_선행_이벤트가_있으면_같은_예약의_후속_이벤트는_조회하지_않는다() {
		PaymentOutboxEvent failedConfirmed = event("R-001", "CONFIRMED");
		failedConfirmed.markFailed();
		outboxRepository.save(failedConfirmed);
		PaymentOutboxEvent blockedDeposit = outboxRepository.save(event("R-001", "DEPOSIT_RECEIVED"));
		PaymentOutboxEvent otherReservation = outboxRepository.save(event("R-002", "CONFIRMED"));
		PaymentOutboxEvent otherTopicSameKey = outboxRepository.save(PaymentOutboxEvent.create(
			"payment.membership", "R-001", "{}", "CONFIRMED"
		));
		outboxRepository.flush();

		List<PaymentOutboxEvent> pendingHeads = outboxRepository.findRelayHeads(
			OutboxStatus.PENDING, ACTIVE_STATUSES, PageRequest.of(0, 100)
		);
		List<PaymentOutboxEvent> failedHeads = outboxRepository.findRelayHeads(
			OutboxStatus.FAILED, ACTIVE_STATUSES, PageRequest.of(0, 100)
		);

		assertThat(pendingHeads).containsExactly(otherReservation, otherTopicSameKey);
		assertThat(pendingHeads).doesNotContain(blockedDeposit);
		assertThat(failedHeads).containsExactly(failedConfirmed);
	}

	@Test
	void 선행_이벤트가_발행되면_같은_예약의_후속_이벤트가_새_선두가_된다() {
		PaymentOutboxEvent confirmed = outboxRepository.save(event("R-001", "CONFIRMED"));
		PaymentOutboxEvent deposit = outboxRepository.save(event("R-001", "DEPOSIT_RECEIVED"));
		outboxRepository.flush();
		confirmed.markPublished();
		outboxRepository.flush();

		List<PaymentOutboxEvent> pendingHeads = outboxRepository.findRelayHeads(
			OutboxStatus.PENDING, ACTIVE_STATUSES, PageRequest.of(0, 100)
		);

		assertThat(pendingHeads).containsExactly(deposit);
	}

	@Test
	void 실패_이벤트가_100건_있어도_신규_예약의_PENDING_이벤트를_별도_배치로_조회한다() {
		IntStream.range(0, 100).forEach(index -> {
			PaymentOutboxEvent failed = event("FAILED-" + index, "CONFIRMED");
			failed.markFailed();
			outboxRepository.save(failed);
		});
		PaymentOutboxEvent pending = outboxRepository.save(event("R-NEW", "CONFIRMED"));
		outboxRepository.flush();

		List<PaymentOutboxEvent> pendingHeads = outboxRepository.findRelayHeads(
			OutboxStatus.PENDING, ACTIVE_STATUSES, PageRequest.of(0, 100)
		);

		assertThat(pendingHeads).containsExactly(pending);
	}

	@Test
	void FAILED_배치는_ID보다_retryCount가_낮은_이벤트를_우선_조회한다() {
		PaymentOutboxEvent manyRetries = event("R-OLD", "CONFIRMED");
		manyRetries.markFailed();
		manyRetries.markFailed();
		manyRetries.markFailed();
		outboxRepository.save(manyRetries);
		PaymentOutboxEvent fewerRetries = event("R-NEW", "CONFIRMED");
		fewerRetries.markFailed();
		outboxRepository.save(fewerRetries);
		outboxRepository.flush();

		List<PaymentOutboxEvent> failedHeads = outboxRepository.findRelayHeads(
			OutboxStatus.FAILED, ACTIVE_STATUSES, PageRequest.of(0, 1)
		);

		assertThat(failedHeads).containsExactly(fewerRetries);
	}

	@Test
	void FAILED_재시도가_성공해_PUBLISHED로_커밋되면_후속_이벤트가_새_선두가_된다() {
		PaymentOutboxEvent failedConfirmed = event("R-001", "CONFIRMED");
		failedConfirmed.markFailed();
		outboxRepository.save(failedConfirmed);
		PaymentOutboxEvent pendingDeposit = outboxRepository.save(event("R-001", "DEPOSIT_RECEIVED"));
		outboxRepository.flush();
		KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
		given(kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(CompletableFuture.completedFuture(null));
		OutboxRelayExecutor executor = new OutboxRelayExecutor(kafkaTemplate);

		List<PaymentOutboxEvent> failedHeads = outboxRepository.findRelayHeads(
			OutboxStatus.FAILED, ACTIVE_STATUSES, PageRequest.of(0, 100)
		);
		executor.execute(failedHeads);
		outboxRepository.flush();
		List<PaymentOutboxEvent> pendingHeads = outboxRepository.findRelayHeads(
			OutboxStatus.PENDING, ACTIVE_STATUSES, PageRequest.of(0, 100)
		);

		assertThat(failedConfirmed.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(pendingHeads).containsExactly(pendingDeposit);
	}

	private PaymentOutboxEvent event(String reservationNumber, String eventType) {
		return PaymentOutboxEvent.create("payment.booking", reservationNumber, "{}", eventType);
	}
}

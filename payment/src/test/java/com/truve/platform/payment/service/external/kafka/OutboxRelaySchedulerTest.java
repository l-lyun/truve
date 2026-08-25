package com.truve.platform.payment.service.external.kafka;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.common.outbox.OutboxRelayExecutor;
import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;
import com.truve.platform.payment.service.repository.PaymentOutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {
	@Mock
	private PaymentOutboxEventRepository outboxRepository;
	@Mock
	private OutboxRelayExecutor outboxRelayExecutor;

	@InjectMocks
	private OutboxRelayScheduler scheduler;

	@Test
	void 최초_발행과_이전_실패_Outbox를_함께_재시도한다() {
		PaymentOutboxEvent event = PaymentOutboxEvent.create(
			"payment.booking", "R-001", "{}", "CONFIRMED"
		);
		given(outboxRepository.findTop100ByStatusInOrderByIdAsc(
			List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)))
			.willReturn(List.of(event));

		scheduler.relay();

		verify(outboxRelayExecutor).execute(List.of(event));
	}
}

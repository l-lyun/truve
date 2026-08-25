package com.truve.platform.payment.service.external.kafka;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
	void 신규와_실패_Outbox의_예약별_선두를_나눠_조회하고_신규_배치를_먼저_처리한다() {
		PaymentOutboxEvent failed = event("R-001", "CONFIRMED", 1L);
		PaymentOutboxEvent pending = event("R-002", "CONFIRMED", 2L);
		List<OutboxStatus> activeStatuses = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
		PageRequest batch = PageRequest.of(0, 100);
		given(outboxRepository.findRelayHeads(OutboxStatus.PENDING, activeStatuses, batch))
			.willReturn(List.of(pending));
		given(outboxRepository.findRelayHeads(OutboxStatus.FAILED, activeStatuses, batch))
			.willReturn(List.of(failed));

		scheduler.relay();

		verify(outboxRelayExecutor).execute(List.of(pending, failed));
	}

	private PaymentOutboxEvent event(String reservationNumber, String eventType, Long id) {
		PaymentOutboxEvent event = PaymentOutboxEvent.create(
			"payment.booking", reservationNumber, "{}", eventType
		);
		ReflectionTestUtils.setField(event, "id", id);
		return event;
	}
}

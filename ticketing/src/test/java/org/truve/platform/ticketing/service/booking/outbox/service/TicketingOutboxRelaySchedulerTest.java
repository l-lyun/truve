package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxRelayExecutor;
import com.truve.platform.common.outbox.OutboxStatus;

@ExtendWith(MockitoExtension.class)
class TicketingOutboxRelaySchedulerTest {
	@Mock
	private TicketingOutboxEventRepository outboxRepository;
	@Mock
	private OutboxRelayExecutor outboxRelayExecutor;

	@InjectMocks
	private TicketingOutboxRelayScheduler scheduler;

	@Test
	void 신규와_실패_Outbox의_예약별_선두를_조회하고_신규를_먼저_전송한다() {
		TicketingOutboxEvent failed = event("R-001", 1L);
		TicketingOutboxEvent pending = event("R-002", 2L);
		List<OutboxStatus> activeStatuses = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
		PageRequest batch = PageRequest.of(0, 100);
		given(outboxRepository.findRelayHeads(OutboxStatus.PENDING, activeStatuses, batch))
			.willReturn(List.of(pending));
		given(outboxRepository.findRelayHeads(OutboxStatus.FAILED, activeStatuses, batch))
			.willReturn(List.of(failed));

		scheduler.relay();

		verify(outboxRelayExecutor).execute(List.of(pending, failed));
	}

	@Test
	void 전송할_Outbox가_없으면_Relay를_호출하지_않는다() {
		List<OutboxStatus> activeStatuses = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
		PageRequest batch = PageRequest.of(0, 100);
		given(outboxRepository.findRelayHeads(OutboxStatus.PENDING, activeStatuses, batch))
			.willReturn(List.of());
		given(outboxRepository.findRelayHeads(OutboxStatus.FAILED, activeStatuses, batch))
			.willReturn(List.of());

		scheduler.relay();

		verify(outboxRelayExecutor, never()).execute(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void 발행완료_Outbox를_정리한다() {
		scheduler.deletePublished();

		verify(outboxRepository).deleteByStatus(OutboxStatus.PUBLISHED);
	}

	private TicketingOutboxEvent event(String reservationNumber, Long id) {
		TicketingOutboxEvent event = TicketingOutboxEvent.create(
			"booking.ticketing", reservationNumber, "{}", "SOLD_CONFIRMED"
		);
		ReflectionTestUtils.setField(event, "id", id);
		return event;
	}
}

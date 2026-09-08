package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class TicketingOutboxRelaySchedulerTest {
	@Mock
	private TicketingOutboxEventRepository outboxRepository;
	@Mock
	private TicketingOutboxClaimService claimService;
	@Mock
	private TicketingOutboxMessageRelay messageRelay;

	@InjectMocks
	private TicketingOutboxRelayScheduler scheduler;

	@Test
	void claim한_이벤트만_Kafka로_전달하고_소유권을_포함한_결과를_반영한다() {
		ClaimedOutboxEvent claimed = claimedEvent();
		OutboxRelayResult result = OutboxRelayResult.published(claimed);
		given(claimService.claimBatch(100)).willReturn(List.of(claimed));
		given(messageRelay.relay(List.of(claimed))).willReturn(List.of(result));

		scheduler.relay();

		verify(messageRelay).relay(List.of(claimed));
		verify(claimService).complete(List.of(result));
	}

	@Test
	void 장애_게이트는_claim_반환_후_발행_전에_실행되고_중단하면_발행하지_않는다() {
		OutboxClaimFaultGate gate = org.mockito.Mockito.mock(OutboxClaimFaultGate.class);
		scheduler.configureFaultGate(gate);
		List<ClaimedOutboxEvent> events = List.of(claimedEvent());
		given(claimService.claimBatch(100)).willReturn(events);
		org.mockito.Mockito.doThrow(new IllegalStateException("interrupted"))
			.when(gate).afterClaimCommitted(events);

		scheduler.relay();

		org.mockito.InOrder order = org.mockito.Mockito.inOrder(claimService, gate);
		order.verify(claimService).claimBatch(100);
		order.verify(gate).afterClaimCommitted(events);
		verify(messageRelay, never()).relay(org.mockito.ArgumentMatchers.anyList());
		verify(claimService, never()).complete(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void claim할_Outbox가_없으면_Kafka와_결과반영을_호출하지_않는다() {
		given(claimService.claimBatch(100)).willReturn(List.of());

		scheduler.relay();

		verify(messageRelay, never()).relay(org.mockito.ArgumentMatchers.anyList());
		verify(claimService, never()).complete(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void claim_중_예외가_발생하면_해당_배치를_종료하고_다음_스케줄에_맡긴다() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		scheduler.configureMetrics(registry);
		given(claimService.claimBatch(100)).willThrow(new IllegalStateException("invalid outbox state"));

		assertThatCode(scheduler::relay).doesNotThrowAnyException();
		assertThat(registry.get("ticketing.outbox.relay.batch").timer().count()).isEqualTo(1);

		verify(messageRelay, never()).relay(org.mockito.ArgumentMatchers.anyList());
		verify(claimService, never()).complete(org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void 설정된_timeout보다_오래된_claim을_회수한다() {
		ReflectionTestUtils.setField(scheduler, "claimTimeoutMs", 60_000L);
		LocalDateTime before = LocalDateTime.now().minusMinutes(1).minusSeconds(1);
		ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

		scheduler.recoverExpiredClaims();

		verify(claimService).recoverExpiredClaims(cutoffCaptor.capture());
		assertThat(cutoffCaptor.getValue()).isAfter(before);
		assertThat(cutoffCaptor.getValue()).isBeforeOrEqualTo(LocalDateTime.now().minusMinutes(1));
	}

	@Test
	void 발행완료_Outbox를_정리한다() {
		scheduler.deletePublished();

		verify(outboxRepository).deleteByStatus(OutboxStatus.PUBLISHED);
	}

	private ClaimedOutboxEvent claimedEvent() {
		return new ClaimedOutboxEvent(
			1L,
			"booking.ticketing",
			"R-001",
			"{}",
			"SOLD_CONFIRMED",
			UUID.randomUUID()
		);
	}
}

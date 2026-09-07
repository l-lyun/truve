package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

@DataJpaTest
@Import({TicketingOutboxClaimService.class, TicketingOutboxClaimServiceIntegrationTest.MetricsConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketingOutboxClaimServiceIntegrationTest {

	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private TicketingOutboxClaimService claimService;
	@Autowired
	private SimpleMeterRegistry registry;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@TestConfiguration
	static class MetricsConfig {
		@Bean
		SimpleMeterRegistry registry() {
			return new SimpleMeterRegistry();
		}
	}

	@Test
	void claimBatch는_데이터소스의_기본_격리수준을_사용한다() throws NoSuchMethodException {
		AnnotationTransactionAttributeSource attributeSource = new AnnotationTransactionAttributeSource();
		TransactionAttribute attribute = attributeSource.getTransactionAttribute(
			TicketingOutboxClaimService.class.getMethod("claimBatch", int.class),
			TicketingOutboxClaimService.class
		);

		assertThat(attribute).isNotNull();
		assertThat(attribute.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
	}

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
	}

	@Test
	void claimToken이_일치하는_Relay만_PUBLISHED로_변경할_수_있다() {
		double publishedBefore = completionCount("published");
		double staleBefore = completionCount("stale");
		outboxRepository.saveAndFlush(event("R-001"));
		ClaimedOutboxEvent claimed = claimService.claimBatch(100).getFirst();

		claimService.complete(List.of(new OutboxRelayResult(
			claimed.id(), UUID.randomUUID(), true
		)));

		TicketingOutboxEvent notOwned = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(notOwned.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(notOwned.getClaimToken()).isEqualTo(claimed.claimToken());
		assertThat(notOwned.getPublishedAt()).isNull();
		assertThat(completionCount("published")).isEqualTo(publishedBefore);
		assertThat(completionCount("stale")).isEqualTo(staleBefore + 1);

		claimService.complete(List.of(OutboxRelayResult.published(claimed)));

		TicketingOutboxEvent published = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(published.getClaimToken()).isNull();
		assertThat(published.getClaimedAt()).isNull();
		assertThat(published.getPublishedAt()).isNotNull();
		assertThat(completionCount("published")).isEqualTo(publishedBefore + 1);
	}

	@Test
	void 완료_트랜잭션이_롤백되면_발행시각과_메트릭을_기록하지_않는다() {
		outboxRepository.saveAndFlush(event("ROLLBACK"));
		ClaimedOutboxEvent claimed = claimService.claimBatch(100).getFirst();
		double publishedBefore = completionCount("published");
		double staleBefore = completionCount("stale");
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			claimService.complete(List.of(OutboxRelayResult.published(claimed),
				new OutboxRelayResult(claimed.id(), UUID.randomUUID(), true)));
			assertThat(completionCount("published")).isEqualTo(publishedBefore);
			status.setRollbackOnly();
		});
		TicketingOutboxEvent stored = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(stored.getPublishedAt()).isNull();
		assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(stored.getClaimToken()).isEqualTo(claimed.claimToken());
		assertThat(completionCount("published")).isEqualTo(publishedBefore);
		assertThat(completionCount("stale")).isEqualTo(staleBefore);
	}

	private double completionCount(String outcome) {
		return registry.get("ticketing.outbox.completion").tag("outcome", outcome).counter().count();
	}

	@Test
	void 발행실패는_소유권을_확인하고_FAILED와_retryCount를_저장한다() {
		double failedBefore = completionCount("failed");
		outboxRepository.saveAndFlush(event("R-001"));
		ClaimedOutboxEvent claimed = claimService.claimBatch(100).getFirst();

		claimService.complete(List.of(OutboxRelayResult.failed(claimed)));

		TicketingOutboxEvent failed = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(failed.getRetryCount()).isEqualTo(1);
		assertThat(failed.getClaimToken()).isNull();
		assertThat(failed.getPublishedAt()).isNull();
		assertThat(completionCount("failed")).isEqualTo(failedBefore + 1);
	}

	@Test
	void timeout을_넘긴_PROCESSING은_FAILED로_회수해_다시_claim할_수_있다() {
		UUID expiredToken = UUID.randomUUID();
		TicketingOutboxEvent expired = event("R-001");
		expired.claim(expiredToken, LocalDateTime.now().minusMinutes(10));
		outboxRepository.saveAndFlush(expired);
		UUID recentToken = UUID.randomUUID();
		TicketingOutboxEvent recent = event("R-002");
		recent.claim(recentToken, LocalDateTime.now().minusMinutes(1));
		outboxRepository.saveAndFlush(recent);

		int recovered = claimService.recoverExpiredClaims(LocalDateTime.now().minusMinutes(5));
		ClaimedOutboxEvent reclaimed = claimService.claimBatch(100).getFirst();
		claimService.complete(List.of(new OutboxRelayResult(expired.getId(), expiredToken, false)));

		assertThat(recovered).isEqualTo(1);
		assertThat(reclaimed.id()).isEqualTo(expired.getId());
		TicketingOutboxEvent processing = outboxRepository.findById(expired.getId()).orElseThrow();
		assertThat(processing.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(processing.getRetryCount()).isEqualTo(1);
		assertThat(processing.getClaimToken()).isEqualTo(reclaimed.claimToken());
		TicketingOutboxEvent notExpired = outboxRepository.findById(recent.getId()).orElseThrow();
		assertThat(notExpired.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(notExpired.getRetryCount()).isZero();
		assertThat(notExpired.getClaimToken()).isEqualTo(recentToken);
	}

	@Test
	void PENDING이_batchSize보다_많아도_FAILED_재시도_batch를_별도로_claim한다() {
		for (int index = 0; index < 101; index++) {
			outboxRepository.save(event("P-" + index));
		}
		TicketingOutboxEvent failed = event("F-001");
		failed.markFailed();
		outboxRepository.saveAndFlush(failed);

		List<ClaimedOutboxEvent> claimed = claimService.claimBatch(100);

		assertThat(claimed).hasSize(101);
		assertThat(claimed).extracting(ClaimedOutboxEvent::id).contains(failed.getId());
	}

	private TicketingOutboxEvent event(String reservationNumber) {
		return TicketingOutboxEvent.create(
			"booking.ticketing", reservationNumber, "{}", "SOLD_CONFIRMED"
		);
	}
}

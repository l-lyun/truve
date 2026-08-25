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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

@DataJpaTest
@Import(TicketingOutboxClaimService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketingOutboxClaimServiceIntegrationTest {

	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private TicketingOutboxClaimService claimService;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
	}

	@Test
	void claimToken이_일치하는_Relay만_PUBLISHED로_변경할_수_있다() {
		outboxRepository.saveAndFlush(event("R-001"));
		ClaimedOutboxEvent claimed = claimService.claimBatch(100).getFirst();

		claimService.complete(List.of(new OutboxRelayResult(
			claimed.id(), UUID.randomUUID(), true
		)));

		TicketingOutboxEvent notOwned = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(notOwned.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(notOwned.getClaimToken()).isEqualTo(claimed.claimToken());

		claimService.complete(List.of(OutboxRelayResult.published(claimed)));

		TicketingOutboxEvent published = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(published.getClaimToken()).isNull();
		assertThat(published.getClaimedAt()).isNull();
	}

	@Test
	void 발행실패는_소유권을_확인하고_FAILED와_retryCount를_저장한다() {
		outboxRepository.saveAndFlush(event("R-001"));
		ClaimedOutboxEvent claimed = claimService.claimBatch(100).getFirst();

		claimService.complete(List.of(OutboxRelayResult.failed(claimed)));

		TicketingOutboxEvent failed = outboxRepository.findById(claimed.id()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(failed.getRetryCount()).isEqualTo(1);
		assertThat(failed.getClaimToken()).isNull();
	}

	@Test
	void timeout을_넘긴_PROCESSING은_FAILED로_회수해_다시_claim할_수_있다() {
		TicketingOutboxEvent expired = event("R-001");
		expired.claim(UUID.randomUUID(), LocalDateTime.now().minusMinutes(10));
		outboxRepository.saveAndFlush(expired);

		int recovered = claimService.recoverExpiredClaims(LocalDateTime.now().minusMinutes(5));
		ClaimedOutboxEvent reclaimed = claimService.claimBatch(100).getFirst();

		assertThat(recovered).isEqualTo(1);
		assertThat(reclaimed.id()).isEqualTo(expired.getId());
		TicketingOutboxEvent processing = outboxRepository.findById(expired.getId()).orElseThrow();
		assertThat(processing.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(processing.getRetryCount()).isEqualTo(1);
		assertThat(processing.getClaimToken()).isEqualTo(reclaimed.claimToken());
	}

	private TicketingOutboxEvent event(String reservationNumber) {
		return TicketingOutboxEvent.create(
			"booking.ticketing", reservationNumber, "{}", "SOLD_CONFIRMED"
		);
	}
}

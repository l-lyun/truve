package org.truve.platform.ticketing.service.booking.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;

import com.truve.platform.common.outbox.OutboxStatus;

@DataJpaTest
class TicketingOutboxEventRepositoryTest {
	@Autowired
	private TicketingOutboxEventRepository outboxRepository;

	@Test
	void 같은_topic과_messageKey의_가장_오래된_활성_이벤트만_claim_대상으로_조회한다() {
		TicketingOutboxEvent first = outboxRepository.save(event("R-001", "SOLD_CONFIRMED"));
		TicketingOutboxEvent blocked = outboxRepository.save(event("R-001", "SALE_CANCELED"));
		TicketingOutboxEvent otherReservation = outboxRepository.save(event("R-002", "SOLD_CONFIRMED"));
		outboxRepository.flush();

		List<TicketingOutboxEvent> heads = outboxRepository.findClaimableHeadsForUpdate(100);

		assertThat(heads).containsExactly(first, otherReservation);
		assertThat(heads).doesNotContain(blocked);
	}

	@Test
	void 선행_이벤트가_PUBLISHED가_되면_후속_이벤트가_새_선두가_된다() {
		TicketingOutboxEvent first = outboxRepository.save(event("R-001", "SOLD_CONFIRMED"));
		TicketingOutboxEvent next = outboxRepository.save(event("R-001", "NEXT_EVENT"));
		outboxRepository.flush();
		first.markPublished();
		outboxRepository.flush();

		List<TicketingOutboxEvent> heads = outboxRepository.findClaimableHeadsForUpdate(100);

		assertThat(heads).containsExactly(next);
	}

	@Test
	void 실패한_SOLD가_남아있으면_후속_판매취소는_선택하지_않는다() {
		TicketingOutboxEvent sold = outboxRepository.save(event("R-001", "SOLD_CONFIRMED"));
		sold.markFailed();
		outboxRepository.save(event("R-001", "SALE_CANCELED"));
		outboxRepository.flush();

		List<TicketingOutboxEvent> heads = outboxRepository.findClaimableHeadsForUpdate(100);

		assertThat(heads).containsExactly(sold);
	}

	private TicketingOutboxEvent event(String reservationNumber, String eventType) {
		return TicketingOutboxEvent.create("booking.ticketing", reservationNumber, "{}", eventType);
	}
}

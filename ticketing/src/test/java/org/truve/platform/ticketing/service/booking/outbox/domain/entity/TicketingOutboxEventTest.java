package org.truve.platform.ticketing.service.booking.outbox.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.truve.platform.common.outbox.OutboxStatus;

class TicketingOutboxEventTest {

	@Test
	void 활성_Outbox를_PROCESSING으로_전환하며_소유권을_기록한다() {
		TicketingOutboxEvent event = event();
		UUID token = UUID.randomUUID();
		LocalDateTime claimedAt = LocalDateTime.of(2026, 8, 25, 15, 0);

		event.claim(token, claimedAt);

		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(event.getClaimToken()).isEqualTo(token);
		assertThat(event.getClaimedAt()).isEqualTo(claimedAt);
	}

	@Test
	void 이미_PROCESSING인_Outbox는_다시_claim할_수_없다() {
		TicketingOutboxEvent event = event();
		event.claim(UUID.randomUUID(), LocalDateTime.now());

		assertThatThrownBy(() -> event.claim(UUID.randomUUID(), LocalDateTime.now()))
			.isInstanceOf(IllegalStateException.class);
	}

	private TicketingOutboxEvent event() {
		return TicketingOutboxEvent.create("booking.ticketing", "R-001", "{}", "SOLD_CONFIRMED");
	}
}

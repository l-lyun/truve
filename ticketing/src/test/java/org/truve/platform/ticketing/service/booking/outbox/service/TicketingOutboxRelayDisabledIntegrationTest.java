package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "ticketing.outbox.claim-enabled=false")
@Import(TicketingOutboxRelayScheduler.class)
class TicketingOutboxRelayDisabledIntegrationTest {
	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void claim_기능이_비활성화되면_Relay_Scheduler를_생성하지_않는다() {
		assertThat(applicationContext.getBeansOfType(TicketingOutboxRelayScheduler.class)).isEmpty();
	}
}

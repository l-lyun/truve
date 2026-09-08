package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;

class HoldPendingExpirationSchedulerConditionTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(ReservationRepository.class, () -> mock(ReservationRepository.class))
		.withBean(Clock.class, Clock::systemUTC)
		.withUserConfiguration(HoldPendingExpirationScheduler.class);

	@Test
	void enabledByDefault() {
		contextRunner.run(context -> assertThat(context).hasSingleBean(HoldPendingExpirationScheduler.class));
	}

	@Test
	void canBeDisabledForReadOnlyBenchmark() {
		contextRunner.withPropertyValues("ticketing.hold.expiration-enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(HoldPendingExpirationScheduler.class));
	}
}

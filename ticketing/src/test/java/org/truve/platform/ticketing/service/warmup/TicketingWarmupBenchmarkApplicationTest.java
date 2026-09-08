package org.truve.platform.ticketing.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.truve.platform.ticketing.service.TicketingApplication;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentClient;
import org.truve.platform.ticketing.service.booking.outbox.service.TicketingOutboxRelayScheduler;
import org.truve.platform.ticketing.service.ticketing.external.kafka.BookingConsumer;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentConsumer;
import org.truve.platform.ticketing.service.ticketing.service.HoldPendingExpirationScheduler;

@SpringBootTest(classes = TicketingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {
		"spring.config.location=file:src/main/resources/application.yml",
		"spring.profiles.active=warmup-benchmark",
		"spring.datasource.url=jdbc:h2:mem:warmup-benchmark-profile;MODE=MySQL;NON_KEYWORDS=VALUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		// 테스트 DB만 생성한다. 실험 프로필의 validate 설정은 별도 프로필 테스트에서 확인한다.
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"ticketing.warmup.enabled=false"
	})
class TicketingWarmupBenchmarkApplicationTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	KafkaListenerEndpointRegistry listenerRegistry;

	@MockitoBean
	PaymentClient paymentClient;

	@Test
	void benchmarkDoesNotStartConsumersOrBackgroundMutations() {
		assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("warmup-benchmark");
		assertThat(context.getBeansOfType(HoldPendingExpirationScheduler.class)).isEmpty();
		assertThat(context.getBeansOfType(TicketingOutboxRelayScheduler.class)).isEmpty();
		assertThat(context.getBeansOfType(StartupTrafficGateFilter.class)).hasSize(1);
		assertThat(listenerRegistry.getListenerContainers()).hasSize(2)
			.allSatisfy(container -> {
				assertThat(container.isAutoStartup()).isFalse();
				assertThat(container.isRunning()).isFalse();
			});
		assertThat(listenerRegistry.getListenerContainers())
			.extracting(container -> container.getContainerProperties().getGroupId())
			.containsExactlyInAnyOrder(BookingConsumer.GROUP, PaymentConsumer.GROUP);
	}
}

package org.truve.platform.ticketing.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class TicketingWarmupBenchmarkProfileTest {

	@Test
	void benchmarkProfileLoadsWithoutLocalOverrides() {
		new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withPropertyValues(
				"spring.config.location=file:src/main/resources/application.yml",
				"spring.profiles.active=warmup-benchmark")
			.run(context -> {
				assertThat(context).hasNotFailed();
				Environment environment = context.getEnvironment();
				assertThat(environment.getActiveProfiles()).containsExactly("warmup-benchmark");
				assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
				assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
				assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
				assertThat(environment.getProperty("spring.kafka.listener.auto-startup", Boolean.class)).isFalse();
				assertThat(environment.getProperty("spring.kafka.admin.auto-create", Boolean.class)).isFalse();
				assertThat(environment.getProperty("ticketing.outbox.claim-enabled", Boolean.class)).isFalse();
				assertThat(environment.getProperty("ticketing.hold.expiration-enabled", Boolean.class)).isFalse();
				assertThat(environment.getProperty("ticketing.startup.gate-enabled", Boolean.class)).isTrue();
				assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("5000");
				assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.connectTimeout"))
					.isEqualTo("5000");
				assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout"))
					.isEqualTo("5000");
				assertThat(environment.getProperty("spring.data.redis.connect-timeout")).isEqualTo("5s");
				assertThat(environment.getProperty("spring.data.redis.timeout")).isEqualTo("5s");
			});
	}
}

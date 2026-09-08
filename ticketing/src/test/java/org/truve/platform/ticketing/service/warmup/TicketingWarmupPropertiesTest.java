package org.truve.platform.ticketing.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TicketingWarmupPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfig.class);

	@Test
	void 기본값은_웜업을_끄고_회차_지정을_요구하지_않는다() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(TicketingWarmupProperties.class).enabled()).isFalse();
		});
	}

	@Test
	void 활성화할_때는_회차가_필수다() {
		contextRunner.withPropertyValues("ticketing.warmup.enabled=true")
			.run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"show-schedule-id=0", "iterations=0", "iterations=1001", "max-duration=0s",
		"max-duration=301s", "query-timeout-seconds=0", "query-timeout-seconds=31"
	})
	void 잘못된_활성화_설정은_기동에_실패한다(String invalid) {
		contextRunner.withPropertyValues("ticketing.warmup.enabled=true", "ticketing.warmup.show-schedule-id=1",
			"ticketing.warmup." + invalid).run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(TicketingWarmupProperties.class)
	static class PropertiesConfig { }
}

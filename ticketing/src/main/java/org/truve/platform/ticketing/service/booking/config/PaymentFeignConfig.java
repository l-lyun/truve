package org.truve.platform.ticketing.service.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Retryer;

@Configuration
public class PaymentFeignConfig {

	@Bean
	public Retryer retryer() {
		return new Retryer.Default(100, 1000, 3);
	}
}
package org.truve.platform.ticketing.service.ticketing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ticketing.kafka.booking-consumer")
public class BookingConsumerKafkaProperties {
	@NotBlank
	private String dltTopic = "booking.ticketing.dlt";

	@NotNull
	private Duration retryInterval = Duration.ofSeconds(1);

	@Positive
	private long maxAttempts = 3;
}

package org.truve.platform.ticketing.service.ticketing.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.truve.platform.common.exception.CustomException;

@Configuration
public class BookingConsumerKafkaConfig {
	public static final String CONTAINER_FACTORY = "bookingTicketingKafkaListenerContainerFactory";

	@Bean(CONTAINER_FACTORY)
	ConcurrentKafkaListenerContainerFactory<Object, Object> bookingTicketingKafkaListenerContainerFactory(
		ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
		ConsumerFactory<Object, Object> consumerFactory,
		KafkaTemplate<Object, Object> kafkaTemplate,
		BookingConsumerKafkaProperties properties
	) {
		ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
			new ConcurrentKafkaListenerContainerFactory<>();
		configurer.configure(factory, consumerFactory);

		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
			kafkaTemplate,
			(record, exception) -> new TopicPartition(properties.getDltTopic(), -1)
		);
		recoverer.excludeHeader(HeadersToAdd.EX_STACKTRACE);
		recoverer.setFailIfSendResultIsError(true);

		DefaultErrorHandler errorHandler = new DefaultErrorHandler(
			recoverer,
			new FixedBackOff(properties.getRetryInterval().toMillis(), properties.getMaxAttempts())
		);
		errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, CustomException.class);
		factory.setCommonErrorHandler(errorHandler);
		return factory;
	}
}

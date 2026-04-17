package com.truve.platform.payment.service.external.kafka.booking;

import org.springframework.stereotype.Component;

import com.truve.platform.common.support.JsonConverter;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;
import com.truve.platform.payment.service.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingPublisher {
	private static final String TOPIC = "payment.booking";

	private final JsonConverter jsonConverter;
	private final PaymentOutboxEventRepository outboxEventRepository;

	public void publish(BookingEventCommand.BookingEvent command) {
		outboxEventRepository.save(PaymentOutboxEvent.create(
			TOPIC,
			command.getReservationNumber(),
			jsonConverter.serialize(command),
			command.getEventType()
		));
	}
}

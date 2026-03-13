package com.truve.platform.payment.service.external.kafka;

import org.springframework.stereotype.Component;

import com.truve.platform.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingPublisher {
	private static final String TOPIC = "payment.booking";

	private final EventPublisher eventPublisher;

	public void publish(BookingEventCommand.BookingEvent command) {
		eventPublisher.publish(TOPIC, command.getReservationNumber(), command.getEventType(), command);
	}
}

package org.truve.platform.ticketing.service.booking.external.kafka;

import org.springframework.stereotype.Component;

import com.truve.platform.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingPublisher {
	private static final String TOPIC = "booking.ticketing";

	private final EventPublisher eventPublisher;

	public void publish(TicketingEventCommand.TicketingEvent command) {
		eventPublisher.publish(TOPIC, command.getReservationNumber(), command.getEventType(), command);
	}
}

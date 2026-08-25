package org.truve.platform.ticketing.service.booking.outbox.service;

import org.springframework.stereotype.Component;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.support.JsonConverter;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingOutboxPublisher {
	static final String TOPIC = "booking.ticketing";

	private final JsonConverter jsonConverter;
	private final TicketingOutboxEventRepository outboxRepository;

	public void publish(TicketingEventCommand.TicketingEvent command) {
		outboxRepository.save(TicketingOutboxEvent.create(
			TOPIC,
			command.getReservationNumber(),
			jsonConverter.serialize(command),
			command.getEventType()
		));
	}
}

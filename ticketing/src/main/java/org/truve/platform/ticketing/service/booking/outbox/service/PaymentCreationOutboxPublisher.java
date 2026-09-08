package org.truve.platform.ticketing.service.booking.outbox.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.support.JsonConverter;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentCreationOutboxPublisher {
	static final String TOPIC = "booking.payment";
	static final String EVENT_TYPE = "CREATE";

	private final JsonConverter jsonConverter;
	private final TicketingOutboxEventRepository outboxRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(PaymentEventCommand.Create command) {
		outboxRepository.save(TicketingOutboxEvent.create(
			TOPIC,
			command.getOrderId(),
			jsonConverter.serialize(command),
			EVENT_TYPE
		));
	}
}

package org.truve.platform.ticketing.service.booking.outbox.service;

import java.util.UUID;

import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;

public record ClaimedOutboxEvent(
	Long id,
	String topic,
	String messageKey,
	String payload,
	String eventType,
	UUID claimToken
) {
	static ClaimedOutboxEvent from(TicketingOutboxEvent event) {
		return new ClaimedOutboxEvent(
			event.getId(),
			event.getTopic(),
			event.getMessageKey(),
			event.getPayload(),
			event.getEventType(),
			event.getClaimToken()
		);
	}
}

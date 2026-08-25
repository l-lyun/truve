package org.truve.platform.ticketing.service.booking.outbox.service;

import java.util.UUID;

public record OutboxRelayResult(Long id, UUID claimToken, boolean published) {
	static OutboxRelayResult published(ClaimedOutboxEvent event) {
		return new OutboxRelayResult(event.id(), event.claimToken(), true);
	}

	static OutboxRelayResult failed(ClaimedOutboxEvent event) {
		return new OutboxRelayResult(event.id(), event.claimToken(), false);
	}
}

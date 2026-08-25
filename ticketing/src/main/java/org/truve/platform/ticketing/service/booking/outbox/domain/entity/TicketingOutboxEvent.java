package org.truve.platform.ticketing.service.booking.outbox.domain.entity;

import com.truve.platform.common.outbox.OutboxEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "ticketing_outbox_events",
	indexes = {
		@Index(name = "idx_ticketing_outbox_status_retry_id", columnList = "status,retry_count,id"),
		@Index(name = "idx_ticketing_outbox_topic_key_id_status", columnList = "topic,message_key,id,status")
	}
)
public class TicketingOutboxEvent extends OutboxEvent {

	private TicketingOutboxEvent(String topic, String messageKey, String payload, String eventType) {
		super(topic, messageKey, payload, eventType);
	}

	public static TicketingOutboxEvent create(
		String topic,
		String messageKey,
		String payload,
		String eventType
	) {
		return new TicketingOutboxEvent(topic, messageKey, payload, eventType);
	}
}

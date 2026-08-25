package org.truve.platform.ticketing.service.booking.inbox.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "ticketing_inbox_events",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_ticketing_inbox_event_id",
		columnNames = "event_id"
	)
)
public class PaymentEventInbox extends BaseEntity {
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(nullable = false, updatable = false)
	private String topic;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private String aggregateId;

	@Column(name = "processed_at", nullable = false, updatable = false)
	private LocalDateTime processedAt;

	private PaymentEventInbox(UUID eventId, String topic, String eventType, String aggregateId) {
		this.eventId = eventId;
		this.topic = topic;
		this.eventType = eventType;
		this.aggregateId = aggregateId;
		this.processedAt = LocalDateTime.now();
	}

	public static PaymentEventInbox processed(
		UUID eventId,
		String topic,
		String eventType,
		String aggregateId
	) {
		return new PaymentEventInbox(eventId, topic, eventType, aggregateId);
	}
}

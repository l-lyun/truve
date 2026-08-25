package org.truve.platform.ticketing.service.booking.outbox.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.truve.platform.common.outbox.OutboxEvent;
import com.truve.platform.common.outbox.OutboxStatus;

import jakarta.persistence.Column;
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
		@Index(name = "idx_ticketing_outbox_topic_key_id_status", columnList = "topic,message_key,id,status"),
		@Index(name = "idx_ticketing_outbox_status_claimed_at", columnList = "status,claimed_at")
	}
)
public class TicketingOutboxEvent extends OutboxEvent {
	@Column(name = "claim_token")
	private UUID claimToken;

	@Column(name = "claimed_at")
	private LocalDateTime claimedAt;

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

	public void claim(UUID token, LocalDateTime now) {
		if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED) {
			throw new IllegalStateException("활성 Outbox만 claim할 수 있습니다.");
		}
		status = OutboxStatus.PROCESSING;
		claimToken = token;
		claimedAt = now;
	}
}

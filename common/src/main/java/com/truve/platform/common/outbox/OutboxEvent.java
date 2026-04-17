package com.truve.platform.common.outbox;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OutboxEvent extends BaseEntity {

	@Column(nullable = false)
	protected String topic;

	@Column(name = "message_key", nullable = false)
	protected String messageKey;

	@Column(columnDefinition = "TEXT", nullable = false)
	protected String payload;

	@Column(name = "event_type", nullable = false)
	protected String eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	protected OutboxStatus status;

	@Column(name = "retry_count", nullable = false)
	protected int retryCount;

	protected OutboxEvent(String topic, String messageKey, String payload, String eventType) {
		this.topic = topic;
		this.messageKey = messageKey;
		this.payload = payload;
		this.eventType = eventType;
		this.status = OutboxStatus.PENDING;
		this.retryCount = 0;
	}

	public void markPublished() {
		this.status = OutboxStatus.PUBLISHED;
	}

	public void markFailed() {
		this.status = OutboxStatus.FAILED;
		this.retryCount++;
	}
}

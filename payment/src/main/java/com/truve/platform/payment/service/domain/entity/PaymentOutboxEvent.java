package com.truve.platform.payment.service.domain.entity;

import com.truve.platform.common.outbox.OutboxEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "payment_outbox_events",
	indexes = {
		@Index(name = "idx_payment_outbox_status_retry_id", columnList = "status,retry_count,id"),
		@Index(name = "idx_payment_outbox_topic_key_id_status", columnList = "topic,message_key,id,status")
	}
)
public class PaymentOutboxEvent extends OutboxEvent {

	@Builder
	public static PaymentOutboxEvent create(String topic, String messageKey, String payload, String eventType) {
		return new PaymentOutboxEvent(topic, messageKey, payload, eventType);
	}

	private PaymentOutboxEvent(String topic, String messageKey, String payload, String eventType) {
		super(topic, messageKey, payload, eventType);
	}
}


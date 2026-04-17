package com.truve.platform.payment.service.domain.entity;

import com.truve.platform.common.outbox.OutboxEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_outbox_events")
public class PaymentOutboxEvent extends OutboxEvent {

	@Builder
	public static PaymentOutboxEvent create(String topic, String messageKey, String payload, String eventType) {
		return new PaymentOutboxEvent(topic, messageKey, payload, eventType);
	}

	private PaymentOutboxEvent(String topic, String messageKey, String payload, String eventType) {
		super(topic, messageKey, payload, eventType);
	}
}


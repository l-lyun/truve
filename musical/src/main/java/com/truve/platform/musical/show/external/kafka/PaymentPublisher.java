package com.truve.platform.musical.show.external.kafka;

import org.springframework.stereotype.Component;

import com.truve.platform.common.event.EventPublisher;

@Component
public class PaymentPublisher {
	private static final String TOPIC = "membership.payment";
	private static final String CREATE_EVENT_TYPE = "CREATE";

	private final EventPublisher eventPublisher;

	public PaymentPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	public void publish(PaymentEventCommand.Create request) {
		eventPublisher.publish(TOPIC, request.getOrderId(), CREATE_EVENT_TYPE, request);
	}
}
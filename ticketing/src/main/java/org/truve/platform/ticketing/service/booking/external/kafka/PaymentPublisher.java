package org.truve.platform.ticketing.service.booking.external.kafka;

import org.springframework.stereotype.Component;

import com.truve.platform.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentPublisher {
	private static final String TOPIC = "booking.payment";
	private static final String CREATE_EVENT_TYPE = "CREATE";

	private final EventPublisher eventPublisher;

	public void publish(PaymentEventCommand.Create request) {
		eventPublisher.publish(TOPIC, request.getOrderId(), CREATE_EVENT_TYPE, request);
	}
}

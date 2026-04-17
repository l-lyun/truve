package com.truve.platform.payment.service.external.kafka.membership;

import org.springframework.stereotype.Component;

import com.truve.platform.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MembershipPublisher {
	private static final String TOPIC = "payment.membership";

	private final EventPublisher eventPublisher;

	public void publish(MembershipEventCommand.MembershipEvent command) {
		eventPublisher.publish(TOPIC, command.getOrderId(), command.getEventType(), command);
	}
}
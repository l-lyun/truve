package com.truve.platform.payment.service.external.kafka.membership;

import org.springframework.stereotype.Component;

import com.truve.platform.common.support.JsonConverter;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;
import com.truve.platform.payment.service.repository.PaymentOutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MembershipPublisher {
	private static final String TOPIC = "payment.membership";

	private final JsonConverter jsonConverter;
	private final PaymentOutboxEventRepository outboxEventRepository;

	public void publish(MembershipEventCommand.MembershipEvent command) {
		outboxEventRepository.save(PaymentOutboxEvent.create(
			TOPIC,
			command.getOrderId(),
			jsonConverter.serialize(command),
			command.getEventType()
		));
	}
}
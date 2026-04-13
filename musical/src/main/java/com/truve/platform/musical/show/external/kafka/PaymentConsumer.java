package com.truve.platform.musical.show.external.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.truve.platform.common.support.JsonConverter;
import com.truve.platform.musical.show.service.MembershipService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {
	public static final String TOPIC = "payment.membership";
	public static final String GROUP = "payment-membership-group";

	private final JsonConverter jsonConverter;
	private final MembershipService membershipService;

	@KafkaListener(topics = TOPIC, groupId = GROUP)
	public void consume(String payload, @Header("event-type") String type) {
		switch (type) {
			case "CONFIRMED" -> membershipService.confirm(
				jsonConverter.convert(payload, MembershipEventCommand.Confirmed.class).getOrderId()
			);
			case "DEPOSIT_RECEIVED" -> membershipService.depositReceive(
				jsonConverter.convert(payload, MembershipEventCommand.DepositReceived.class).getOrderId()
			);
			default -> log.warn("[Kafka Consumer] Unknown event type: {}", type);
		}
	}
}
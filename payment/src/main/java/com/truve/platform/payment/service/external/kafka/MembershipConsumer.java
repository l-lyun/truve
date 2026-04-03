package com.truve.platform.payment.service.external.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.truve.platform.common.support.JsonConverter;
import com.truve.platform.payment.service.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipConsumer {
	public static final String TOPIC = "membership.payment";
	public static final String GROUP = "membership-payment-group";

	private final JsonConverter jsonConverter;
	private final PaymentService paymentService;

	@KafkaListener(topics = TOPIC, groupId = GROUP)
	public void consume(String payload, @Header("event-type") String type) {
		switch (type) {
			case "CREATE" -> handleCreate(payload);
			case "CANCEL" -> handleCancel(payload);
		}
	}

	private void handleCreate(String payload) {
		PaymentEventCommand.Create request = jsonConverter.convert(payload, PaymentEventCommand.Create.class);
		paymentService.create(request);
	}

	private void handleCancel(String payload) {
	}
}
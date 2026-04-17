package com.truve.platform.payment.service.external.kafka.booking;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.truve.platform.common.support.JsonConverter;
import com.truve.platform.payment.service.external.kafka.PaymentEventCommand;
import com.truve.platform.payment.service.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConsumer {
	public static final String TOPIC = "booking.payment";
	public static final String GROUP = "booking-payment-group";

	private final JsonConverter jsonConverter;
	private final PaymentService paymentService;

	@KafkaListener(topics = TOPIC, groupId = GROUP)
	public void consume(String payload, @Header("event-type") String type) {
		if (type.equals("CREATE")) {
			handleCreate(payload);
		}
	}

	private void handleCreate(String payload) {
		PaymentEventCommand.Create request = jsonConverter.convert(payload, PaymentEventCommand.Create.class);
		paymentService.create(request);
	}
}

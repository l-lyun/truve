package org.truve.platform.ticketing.service.booking.external.kafka;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.truve.platform.ticketing.service.booking.inbox.service.PaymentEventInboxHandler;

import com.truve.platform.common.support.JsonConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {
	public static final String TOPIC = "payment.booking";
	public static final String GROUP = "payment-booking-group";

	private final JsonConverter jsonConverter;
	private final PaymentEventInboxHandler paymentEventInboxHandler;

	@KafkaListener(topics = TOPIC, groupId = GROUP)
	public void consume(
		String payload,
		@Header("event-type") String type,
		@Header("event-id") String eventIdHeader
	) {
		UUID eventId = UUID.fromString(eventIdHeader);
		switch (type) {
			case "CONFIRMED" -> paymentEventInboxHandler.handle(
				eventId,
				type,
				jsonConverter.convert(payload, BookingEventCommand.Confirmed.class)
			);
			case "DEPOSIT_RECEIVED" -> paymentEventInboxHandler.handle(
				eventId,
				type,
				jsonConverter.convert(payload, BookingEventCommand.DepositReceived.class)
			);
			default -> log.warn("[Kafka Consumer] Unknown event type: {}", type);
		}
	}
}

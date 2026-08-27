package org.truve.platform.ticketing.service.ticketing.external.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.service.ScheduledSeatStatusService;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedEventHandler;

import com.truve.platform.common.support.JsonConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConsumer {
	public static final String TOPIC = "booking.ticketing";
	public static final String GROUP = "booking-ticketing-group";

	private final JsonConverter jsonConverter;
	private final ScheduledSeatStatusService scheduledSeatStatusService;
	private final HoldRequestedEventHandler holdRequestedEventHandler;

	@KafkaListener(topics = TOPIC, groupId = GROUP)
	public void consume(String payload, @Header("event-type") String type) {
		switch (type) {
			case "HOLD_REQUESTED" ->
				holdRequestedEventHandler.handle(jsonConverter.convert(payload, TicketingEventCommand.HoldRequested.class));
			case "HOLD_RELEASED" ->
				scheduledSeatStatusService.releaseSeats(jsonConverter.convert(payload, TicketingEventCommand.HoldReleased.class));
			case "SOLD_CONFIRMED" ->
				scheduledSeatStatusService.purchaseSeats(jsonConverter.convert(payload, TicketingEventCommand.SoldConfirmed.class));
			case "SALE_CANCELED" ->
				scheduledSeatStatusService.cancelSales(jsonConverter.convert(payload, TicketingEventCommand.SaleCanceled.class));
		}
	}
}

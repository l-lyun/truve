package org.truve.platform.ticketing.service.booking.inbox.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation.PaymentTransitionResult;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.inbox.domain.entity.PaymentEventInbox;
import org.truve.platform.ticketing.service.booking.inbox.repository.PaymentEventInboxRepository;
import org.truve.platform.ticketing.service.booking.service.BookingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProcessor {
	private static final String TOPIC = "payment.booking";

	private final PaymentEventInboxRepository inboxRepository;
	private final BookingService bookingService;

	@Transactional
	public void process(UUID eventId, String eventType, BookingEventCommand.BookingEvent event) {
		if (!eventType.equals(event.getEventType())) {
			throw new IllegalArgumentException("Kafka event-type과 payload eventType이 일치하지 않습니다.");
		}

		inboxRepository.saveAndFlush(PaymentEventInbox.processed(
			eventId,
			TOPIC,
			eventType,
			event.getReservationNumber()
		));

		if (event instanceof BookingEventCommand.Confirmed confirmed) {
			PaymentTransitionResult result = bookingService.confirm(confirmed);
			logTerminalIgnored(eventId, event, result);
			return;
		}
		if (event instanceof BookingEventCommand.DepositReceived depositReceived) {
			PaymentTransitionResult result = bookingService.depositReceive(depositReceived);
			logTerminalIgnored(eventId, event, result);
			return;
		}
		throw new IllegalArgumentException("지원하지 않는 결제 이벤트입니다: " + eventType);
	}

	private void logTerminalIgnored(
		UUID eventId,
		BookingEventCommand.BookingEvent event,
		PaymentTransitionResult result
	) {
		if (result == PaymentTransitionResult.TERMINAL_IGNORED) {
			log.warn("취소 또는 완료된 예약의 결제 이벤트를 상태 변경 없이 기록합니다. eventId={}, reservationNumber={}",
				eventId, event.getReservationNumber());
		}
	}
}

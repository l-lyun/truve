package org.truve.platform.ticketing.service.booking.inbox.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.inbox.domain.entity.PaymentEventInbox;
import org.truve.platform.ticketing.service.booking.inbox.repository.PaymentEventInboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventInboxHandler {
	private final PaymentEventInboxRepository inboxRepository;
	private final PaymentEventProcessor paymentEventProcessor;

	public void handle(UUID eventId, String eventType, BookingEventCommand.BookingEvent event) {
		PaymentEventInbox processed = inboxRepository.findByEventId(eventId).orElse(null);
		if (processed != null) {
			validateSameEvent(processed, eventType, event);
			logDuplicate(eventId, event);
			return;
		}

		try {
			paymentEventProcessor.process(eventId, eventType, event);
		} catch (DataIntegrityViolationException exception) {
			processed = inboxRepository.findByEventId(eventId).orElse(null);
			if (processed != null) {
				validateSameEvent(processed, eventType, event);
				logDuplicate(eventId, event);
				return;
			}
			throw exception;
		}
	}

	private void validateSameEvent(
		PaymentEventInbox processed,
		String eventType,
		BookingEventCommand.BookingEvent event
	) {
		if (!processed.getEventType().equals(eventType)
			|| !processed.getAggregateId().equals(event.getReservationNumber())) {
			throw new IllegalStateException("같은 eventId에 서로 다른 결제 이벤트가 수신됐습니다. eventId="
				+ processed.getEventId());
		}
	}

	private void logDuplicate(UUID eventId, BookingEventCommand.BookingEvent event) {
		log.info("이미 처리한 결제 이벤트입니다. eventId={}, reservationNumber={}",
			eventId, event.getReservationNumber());
	}
}

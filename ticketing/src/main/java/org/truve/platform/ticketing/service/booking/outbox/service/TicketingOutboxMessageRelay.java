package org.truve.platform.ticketing.service.booking.outbox.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketingOutboxMessageRelay {
	private final KafkaTemplate<String, String> kafkaTemplate;

	public List<OutboxRelayResult> relay(List<ClaimedOutboxEvent> events) {
		List<PendingSend> pendingSends = events.stream().map(this::send).toList();
		return pendingSends.stream().map(this::awaitResult).toList();
	}

	private PendingSend send(ClaimedOutboxEvent event) {
		try {
			ProducerRecord<String, String> record =
				new ProducerRecord<>(event.topic(), event.messageKey(), event.payload());
			record.headers().add("event-type", event.eventType().getBytes(StandardCharsets.UTF_8));
			return new PendingSend(event, kafkaTemplate.send(record));
		} catch (RuntimeException exception) {
			return new PendingSend(event, CompletableFuture.failedFuture(exception));
		}
	}

	private OutboxRelayResult awaitResult(PendingSend pendingSend) {
		ClaimedOutboxEvent event = pendingSend.event();
		try {
			pendingSend.future().get();
			log.info("[Ticketing Outbox Relay] Published - id: {}, topic: {}, key: {}, eventType: {}",
				event.id(), event.topic(), event.messageKey(), event.eventType());
			return OutboxRelayResult.published(event);
		} catch (Exception exception) {
			log.error("[Ticketing Outbox Relay] Failed - id: {}, claimToken: {}",
				event.id(), event.claimToken(), exception);
			return OutboxRelayResult.failed(event);
		}
	}

	private record PendingSend(
		ClaimedOutboxEvent event,
		CompletableFuture<SendResult<String, String>> future
	) {
	}
}

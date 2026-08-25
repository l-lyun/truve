package com.truve.platform.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayExecutor {

	private final KafkaTemplate<String, String> kafkaTemplate;

	public void execute(List<? extends OutboxEvent> pendingEvents) {
		for (OutboxEvent event : pendingEvents) {
			try {
				ProducerRecord<String, String> record =
					new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
				record.headers().add("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8));

				kafkaTemplate.send(record).get();

				event.markPublished();
				log.info("[Outbox Relay] Published - topic: {}, key: {}, eventType: {}, payload: {}",
					event.getTopic(), event.getMessageKey(), event.getEventType(), event.getPayload());
			} catch (Exception e) {
				event.markFailed();
				log.error("[Outbox Relay] Failed - id: {}, retryCount: {}", event.getId(), event.getRetryCount(), e);
			}
		}
	}
}

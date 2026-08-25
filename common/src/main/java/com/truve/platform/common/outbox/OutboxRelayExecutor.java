package com.truve.platform.common.outbox;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		Set<String> failedMessageKeys = new HashSet<>();
		for (OutboxEvent event : pendingEvents) {
			if (failedMessageKeys.contains(event.getMessageKey())) {
				continue;
			}
			try {
				ProducerRecord<String, String> record =
					new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
				record.headers().add("event-id", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
				record.headers().add("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8));

				kafkaTemplate.send(record).get();

				event.markPublished();
				log.info("[Outbox Relay] Published - topic: {}, key: {}, eventType: {}, payload: {}",
					event.getTopic(), event.getMessageKey(), event.getEventType(), event.getPayload());
			} catch (Exception e) {
				event.markFailed();
				failedMessageKeys.add(event.getMessageKey());
				log.error("[Outbox Relay] Failed - id: {}, retryCount: {}", event.getId(), event.getRetryCount(), e);
			}
		}
	}
}

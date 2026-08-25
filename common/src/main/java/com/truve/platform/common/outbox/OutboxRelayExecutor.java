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
		Set<RelayKey> failedKeys = new HashSet<>();
		for (OutboxEvent event : pendingEvents) {
			RelayKey relayKey = new RelayKey(event.getTopic(), event.getMessageKey());
			if (failedKeys.contains(relayKey)) {
				continue;
			}
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
				failedKeys.add(relayKey);
				log.error("[Outbox Relay] Failed - id: {}, retryCount: {}", event.getId(), event.getRetryCount(), e);
			}
		}
	}

	private record RelayKey(String topic, String messageKey) {
	}
}

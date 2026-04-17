package com.truve.platform.common.event;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.truve.platform.common.support.JsonConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonConverter jsonConverter;

	@Override
	public void publish(String topic, String key, Object event) {
		String payload = jsonConverter.serialize(event);
		log.info("[Kafka Publish] Start - Topic: {}, Key: {}, Payload: {}", topic, key, payload);

		kafkaTemplate.send(topic, key, payload);
	}

	@Override
	public void publish(String topic, String key, String type, Object event) {
		String payload = jsonConverter.serialize(event);
		log.info("[Kafka Publish With Header] Topic: {}, event-type: {}, Payload: {}", topic, type, payload);

		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
		record.headers().add("event-type", type.getBytes());

		kafkaTemplate.send(record);
	}
}

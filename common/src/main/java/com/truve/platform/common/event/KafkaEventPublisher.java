package com.truve.platform.common.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	@Override
	public void publish(String topic, String key, Object event) {
		try {
			String payload = objectMapper.writeValueAsString(event);
			kafkaTemplate.send(topic, key, payload);
		} catch (JsonProcessingException exception) {
			throw new CustomException(ErrorCode.EVENT_SERIALIZATION_FAILED);
		} catch (Exception exception) {
			throw new CustomException(ErrorCode.EVENT_PUBLISH_FAILED);
		}
	}
}

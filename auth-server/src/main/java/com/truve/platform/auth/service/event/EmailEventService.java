package com.truve.platform.auth.service.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailEventService {
	private final KafkaTemplate<String, String> kafkaTemplate;
	public void sendEmail(String email) {
		this.kafkaTemplate.send(
			"email.send",
			email
		);
	}
}

package com.truve.platform.auth.service.event;

import org.springframework.stereotype.Service;

import com.truve.platform.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailEventService {

	private static final String EMAIL_SEND_TOPIC = "email.send";

	private final EventPublisher eventPublisher;

	public void sendEmail(EmailSendEvent event) {
		eventPublisher.publish(EMAIL_SEND_TOPIC, event.getEmail(), event);
	}
}

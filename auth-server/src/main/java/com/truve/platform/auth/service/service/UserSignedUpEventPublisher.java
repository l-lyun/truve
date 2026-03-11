package com.truve.platform.auth.service.service;

import org.springframework.stereotype.Component;

import com.truve.platform.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserSignedUpEventPublisher {

	private static final String USER_SIGNED_UP_TOPIC = "user.signedup.event";
	private final EventPublisher eventPublisher;

	public void publish(String key, Object event) {
		eventPublisher.publish(USER_SIGNED_UP_TOPIC, key, event);
	}

}

package com.truve.platform.common.event;

public interface EventPublisher {
	void publish(String topic, String key, Object event);

	void publish(String topic, String key, String type, Object event);
}

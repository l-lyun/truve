package org.truve.platform.queue.service.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

	// TODO: 환경변수 동적 변경 필요
	private int permitPerTick = 100;
	private long schedulerDelayMs = 500;
	private long readyTtlSec = 100;
	private boolean schedulerEnabled = true;
	private long activeWindowMs;
	private long activeLimit;

}

package org.truve.platform.ticketing.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "ticketing")
public class TicketingProperties {

	private long activeWindowMs = 30000L;
	private long sessionTtlSec = 300L;
}

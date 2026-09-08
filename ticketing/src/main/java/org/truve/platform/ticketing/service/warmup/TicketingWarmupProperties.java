package org.truve.platform.ticketing.service.warmup;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ticketing.warmup")
public record TicketingWarmupProperties(
	@DefaultValue("false") boolean enabled,
	Long showScheduleId,
	@DefaultValue("100") int iterations,
	@DefaultValue("30s") Duration maxDuration,
	@DefaultValue("5") int queryTimeoutSeconds
) {
	public TicketingWarmupProperties {
		if (enabled) {
			if (showScheduleId == null || showScheduleId <= 0) {
				throw new IllegalArgumentException("웜업 회차 ID는 양수로 지정해야 합니다.");
			}
			if (iterations < 1 || iterations > 1000) {
				throw new IllegalArgumentException("웜업 반복 횟수는 1~1000이어야 합니다.");
			}
			if (maxDuration == null || maxDuration.isNegative() || maxDuration.isZero()
				|| maxDuration.compareTo(Duration.ofMinutes(5)) > 0) {
				throw new IllegalArgumentException("웜업 실행 예산은 0초 초과, 5분 이하여야 합니다.");
			}
			if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 30) {
				throw new IllegalArgumentException("웜업 조회 제한 시간은 1~30초여야 합니다.");
			}
		}
	}
}

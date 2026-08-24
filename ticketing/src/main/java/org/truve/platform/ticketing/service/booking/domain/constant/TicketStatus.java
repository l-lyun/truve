package org.truve.platform.ticketing.service.booking.domain.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus {
	PENDING("발급 대기"),
	ISSUED("발급"),
	USED("사용"),
	CANCELED("취소"),
	;

	private final String description;
}

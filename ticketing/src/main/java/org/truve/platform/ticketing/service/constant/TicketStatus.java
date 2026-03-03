package org.truve.platform.ticketing.service.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus {
	ISSUED("발급"),
	USED("사용"),
	CANCELED("취소"),
	;

	private final String description;
}

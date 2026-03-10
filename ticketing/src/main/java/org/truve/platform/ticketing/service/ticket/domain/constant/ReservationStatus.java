package org.truve.platform.ticketing.service.ticket.domain.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
	CREATED("생성"),
	CONFIRMED("예매확정"),
	COMPLETED("관람완료"),
	PARTIAL_CANCELED("부분취소"),
	CANCELED("취소"),
	;

	private final String description;
}

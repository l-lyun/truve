package org.truve.platform.ticketing.service.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
	CREATED("생성"),
	PAID("결제 완료"),
	CANCELED("취소"),
	EXPIRED("만료"),
	;

	private final String description;
}

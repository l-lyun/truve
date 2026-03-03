package org.truve.platform.ticketing.service.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatStatus {
	AVAILABLE("예매 가능"),
	HOLD("좌석 선점"),
	SOLD("판매된 좌석"),
	;

	private final String description;

}

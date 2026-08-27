package org.truve.platform.ticketing.service.booking.domain.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
	CREATED("생성"),
	HOLD_PENDING("좌석 선점 반영 중"),
	PAYMENT_READY("결제 가능"),
	HOLD_FAILED("좌석 선점 실패"),
	EXPIRED("만료"),
	PENDING_PAYMENT("결제 진행 중"),
	PENDING_DEPOSIT("입금대기"),
	CONFIRMED("예매확정"),
	COMPLETED("관람완료"),
	PARTIAL_CANCELED("부분취소"),
	CANCELED("취소"),
	;

	private final String description;
}

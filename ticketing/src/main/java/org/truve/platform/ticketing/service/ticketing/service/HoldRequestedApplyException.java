package org.truve.platform.ticketing.service.ticketing.service;

public class HoldRequestedApplyException extends RuntimeException {
	private final FailureReason reason;

	private HoldRequestedApplyException(FailureReason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public static HoldRequestedApplyException expired() {
		return new HoldRequestedApplyException(FailureReason.EXPIRED, "좌석 선점 이벤트가 만료되었습니다.");
	}

	public static HoldRequestedApplyException seatConflict() {
		return new HoldRequestedApplyException(FailureReason.SEAT_CONFLICT, "DB 좌석 소유권이 충돌했습니다.");
	}

	public FailureReason getReason() {
		return reason;
	}

	public enum FailureReason {
		EXPIRED,
		SEAT_CONFLICT
	}
}

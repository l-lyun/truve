package com.truve.platform.musical.show.domain.constant;

public enum MembershipPaymentMethod {
	TOSS_PAY("토스 결제"),
	BANK_TRANSFER("무통장 입금");

	private final String displayName;

	MembershipPaymentMethod(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
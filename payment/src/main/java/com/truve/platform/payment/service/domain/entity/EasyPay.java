package com.truve.platform.payment.service.domain.entity;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EasyPay {
	private String provider;
	private Long discountAmount;

	@Builder
	public EasyPay(String provider, Long discountAmount) {
		this.provider = provider;
		this.discountAmount = discountAmount;
	}
}

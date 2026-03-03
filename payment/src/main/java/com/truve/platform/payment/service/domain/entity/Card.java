package com.truve.platform.payment.service.domain.entity;

import com.truve.platform.payment.service.domain.constant.CardCompany;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Card {
	@Enumerated(EnumType.STRING)
	private CardCompany issuer;
	private String number;
	private Integer installmentPlanMonths;

	@Builder
	public Card(String issuerCode, String number, Integer installmentPlanMonths) {
		this.issuer = CardCompany.of(issuerCode);
		this.number = number;
		this.installmentPlanMonths = installmentPlanMonths;
	}
}

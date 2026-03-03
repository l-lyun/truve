package com.truve.platform.payment.service.domain.constant;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentMethod {
	UNCONFIRMED("결제수단 미지정", "결제수단 미지정"),
	CARD("카드", "카드"),
	EASY_PAY("간편결제", "간편결제"),
	VIRTUAL_ACCOUNT("가상계좌", "무통장입금");

	private final String externalName;
	private final String displayName;

	private static final Map<String, PaymentMethod> BY_NAME;

	static {
		BY_NAME = Collections.unmodifiableMap(
			Stream.of(values()).collect(Collectors.toMap(PaymentMethod::getExternalName, Function.identity()))
		);
	}

	public static PaymentMethod of(String name) {
		return BY_NAME.get(name);
	}
}

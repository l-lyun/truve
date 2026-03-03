package com.truve.platform.payment.service.domain.constant;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CardCompany {
	기업BC("3K", "기업비씨"),
	광주("46", "광주은행"),
	롯데("71", "롯데카드"),
	산업("30", "KDB산업은행"),
	BC("31", "BC카드"),
	삼성("51", "삼성카드"),
	새마을("38", "새마을금고"),
	신한("41", "신한카드"),
	신협("62", "신협"),
	씨티("36", "씨티카드"),
	우리BC("33", "우리BC카드"),
	우리("W1", "우리카드"),
	우체국("37", "우체국예금보험"),
	저축("39", "저축은행중앙회"),
	전북("35", "전북은행"),
	제주("42", "제주은행"),
	카카오뱅크("15", "카카오뱅크"),
	케이뱅크("3A", "케이뱅크"),
	토스뱅크("24", "토스뱅크"),
	하나("21", "하나카드"),
	현대("61", "현대카드"),
	국민("11", "KB국민카드"),
	농협("91", "NH농협카드"),
	수협("34", "Sh수협은행");

	private final String cardCompanyCode;
	private final String cardCompanyName;

	private static final Map<String, CardCompany> BY_CODE;

	static {
		BY_CODE = Collections.unmodifiableMap(
			Stream.of(values()).collect(Collectors.toMap(CardCompany::getCardCompanyCode, Function.identity()))
		);
	}

	public static CardCompany of(String cardCompanyCode) {
		return Optional.ofNullable(BY_CODE.get(cardCompanyCode))
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_CARD_COMPANY_CODE));
	}
}

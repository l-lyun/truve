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
public enum Bank {
	경남("39", "경남"),
	광주("34", "광주"),
	국민("06", "KB국민은행"),
	기업("03", "IBK기업은행"),
	농협("11", "NH농협은행"),
	대구("31", "iM뱅크(대구)"),
	부산("32", "부산은행"),
	새마을("45", "새마을금고"),
	수협("07", "Sh수협은행"),
	신한("88", "신한은행"),
	우리("20", "우리은행"),
	우체국("71", "우체국예금보험"),
	하나("81", "하나은행");

	private final String bankCode;
	private final String bankName;

	private static final Map<String, Bank> BY_CODE;

	static {
		BY_CODE = Collections.unmodifiableMap(
			Stream.of(values()).collect(Collectors.toMap(Bank::getBankCode, Function.identity()))
		);
	}

	public static Bank of(String bankCode) {
		return Optional.ofNullable(BY_CODE.get(bankCode))
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_BANK_CODE));
	}
}

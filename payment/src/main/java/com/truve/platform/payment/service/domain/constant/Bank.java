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
	경남("39", "경남은행"),
	광주("34", "광주은행"),
	교보증권("S8", "교보증권"),
	단위농협("12", "단위농협(지역농축협)"),
	대신증권("SE", "대신증권"),
	메리츠증권("SK", "메리츠증권"),
	미래에셋증권("S5", "미래에셋증권"),
	부국("SM", "부국증권"),
	부산("32", "부산은행"),
	삼성증권("S3", "삼성증권"),
	새마을("45", "새마을금고"),
	산림("64", "산림조합"),
	신영증권("SN", "신영증권"),
	신한금융투자("S2", "신한금융투자"),
	신한("88", "신한은행"),
	신협("48", "신협"),
	씨티("27", "씨티은행"),
	우리("20", "우리은행"),
	우체국("71", "우체국예금보험"),
	유안타증권("S0", "유안타증권"),
	유진투자증권("SJ", "유진투자증권"),
	저축("50", "저축은행중앙회"),
	전북("37", "전북은행"),
	제주("35", "제주은행"),
	카카오("90", "카카오뱅크"),
	카카오페이증권("SQ", "카카오페이증권"),
	케이("89", "케이뱅크"),
	키움증권("SB", "키움증권"),
	토스머니("-", "토스머니"),
	토스("92", "토스뱅크"),
	토스증권("ST", "토스증권"),
	펀드온라인코리아("SR", "펀드온라인코리아(한국포스증권)"),
	하나금융투자("SH", "하나금융투자"),
	하나("81", "하나은행"),
	하이투자증권("S9", "하이투자증권"),
	한국투자증권("S6", "한국투자증권"),
	한화투자증권("SG", "한화투자증권"),
	현대차증권("SA", "현대차증권"),
	HSBC("54", "홍콩상하이은행"),
	DB금융투자("SI", "DB금융투자"),
	대구("31", "DGB대구은행"),
	기업("03", "IBK기업은행"),
	국민("06", "KB국민은행"),
	KB증권("S4", "KB증권"),
	산업("02", "KDB산업은행"),
	KTB투자증권("SP", "KTB투자증권(다올투자증권)"),
	LIG투자("SO", "LIG투자증권"),
	농협("11", "NH농협은행"),
	NH투자증권("SL", "NH투자증권"),
	SC제일("23", "SC제일은행"),
	수협("07", "Sh수협은행"),
	SK증권("SD", "SK증권");

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

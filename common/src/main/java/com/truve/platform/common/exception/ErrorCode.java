package com.truve.platform.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	NOT_FOUND_USER(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
	ALREADY_EXISTS_EMAIL(HttpStatus.BAD_REQUEST, "중복된 이메일입니다."),
	NOT_FOUND_EMAIL(HttpStatus.BAD_REQUEST, "이메일을 찾을 수 없습니다."),
	NOT_CORRECT_EMAIL_CODE(HttpStatus.BAD_REQUEST, "이메일 인증 코드가 다릅니다."),
	NOT_CORRECT_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다."),
	NOT_VERIFIED_EMAIL(HttpStatus.BAD_REQUEST, "인증되지 않은 이메일입니다."),
	ALREADY_VERIFIED_EMAIL(HttpStatus.BAD_REQUEST, "이미 인증된 이메일입니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "리프레시 토큰이 올바르지 않습니다."),
	KAKAO_USERINFO_FAILED(HttpStatus.BAD_REQUEST, "카카오 사용자 정보를 가져오지 못했습니다."),

	NOT_FOUND_PAYMENT(HttpStatus.BAD_REQUEST, "존재하지 않는 결제입니다."),
	INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 금액입니다."),
	NOT_ENOUGH_CANCELABLE_AMOUNT(HttpStatus.BAD_REQUEST, "취소 가능 잔액이 부족합니다."),
	ALREADY_CANCELED_PAYMENT(HttpStatus.BAD_REQUEST, "이미 전액 취소된 결제입니다."),
	INVALID_CANCEL_AMOUNT(HttpStatus.BAD_REQUEST, "유효하지 않은 취소 금액입니다."),
	INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "처리할 수 없는 결제 상태입니다."),
	INVALID_PAYMENT_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 키입니다."),
	MUST_CANCEL_FULL(HttpStatus.BAD_REQUEST, "전액 취소만 가능한 건입니다."),
	ALREADY_EXIST_PAYMENT(HttpStatus.BAD_REQUEST, "이미 존재하는 결제입니다."),
	EXTERNAL_PAYMENT_ERROR(HttpStatus.BAD_REQUEST, "결제 대행사 오류가 발생했습니다."),
	INVALID_BANK_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 은행 코드입니다."),
	INVALID_CARD_COMPANY_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 카드사 코드입니다."),
	ALREADY_DONE_PAYMENT(HttpStatus.BAD_REQUEST, "이미 처리된 결제입니다."),
	INVALID_PAYMENT_METHOD_DETAILS(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 수단 정보입니다."),

	JSON_PARSE_ERROR(HttpStatus.BAD_REQUEST, "JSON 파싱 중 오류가 발생했습니다."),
	INVALID_ADMISSION_TOKEN(HttpStatus.BAD_REQUEST, "입장 토큰이 유효하지 않습니다."),
	EXPIRED_ADMISSION_TOKEN(HttpStatus.BAD_REQUEST, "입장 토큰이 만료되었습니다."),
	ADMISSION_TOKEN_MISMATCH(HttpStatus.BAD_REQUEST, "입장 토큰 정보가 요청과 일치하지 않습니다."),
	INVALID_SESSION_TOKEN(HttpStatus.BAD_REQUEST, "세션 토큰이 유효하지 않습니다."),
	SESSION_TOKEN_MISMATCH(HttpStatus.BAD_REQUEST, "세션 토큰 정보가 요청과 일치하지 않습니다."),
	;

	private final HttpStatus status;
	private final String message;

}

package com.truve.platform.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	NOT_FOUND_USER(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다.", "A01"),
	ALREADY_EXISTS_EMAIL(HttpStatus.BAD_REQUEST, "중복된 이메일입니다.", "A02"),
	NOT_FOUND_EMAIL(HttpStatus.BAD_REQUEST, "이메일을 찾을 수 없습니다.", "A03"),
	NOT_CORRECT_EMAIL_CODE(HttpStatus.BAD_REQUEST, "이메일 인증 코드가 다릅니다.", "A04"),
	NOT_CORRECT_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다.", "A05"),
	NOT_VERIFIED_EMAIL(HttpStatus.BAD_REQUEST, "인증되지 않은 이메일입니다.", "A06"),
	ALREADY_VERIFIED_EMAIL(HttpStatus.BAD_REQUEST, "이미 인증된 이메일입니다.", "A07"),
	INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "리프레시 토큰이 올바르지 않습니다.", "A08"),
	KAKAO_USERINFO_FAILED(HttpStatus.BAD_REQUEST, "카카오 사용자 정보를 가져오지 못했습니다.", "A09"),
	REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관 동의가 필요합니다.", "A10"),
	INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "유효하지 않은 닉네임입니다.", "A11"),
	ALREADY_EXISTS_NICKNAME(HttpStatus.BAD_REQUEST, "중복된 닉네임입니다.", "A12"),
	ALREADY_WITHDRAWN_USER(HttpStatus.BAD_REQUEST, "이미 탈퇴한 회원입니다.", "A13"),
	INVALID_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "유효하지 않은 소셜 로그인 제공자입니다.", "A14"),
	INVALID_SOCIAL_REGISTRATION_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 소셜 가입 토큰입니다.", "A15"),

	NOT_FOUND_PAYMENT(HttpStatus.BAD_REQUEST, "존재하지 않는 결제입니다.", "P01"),
	INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 금액입니다.", "P02"),
	NOT_ENOUGH_CANCELABLE_AMOUNT(HttpStatus.BAD_REQUEST, "취소 가능 잔액이 부족합니다.", "P03"),
	ALREADY_CANCELED_PAYMENT(HttpStatus.BAD_REQUEST, "이미 전액 취소된 결제입니다.", "P04"),
	INVALID_CANCEL_AMOUNT(HttpStatus.BAD_REQUEST, "유효하지 않은 취소 금액입니다.", "P05"),
	INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "처리할 수 없는 결제 상태입니다.", "P06"),
	INVALID_PAYMENT_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 키입니다.", "P07"),
	MUST_CANCEL_FULL(HttpStatus.BAD_REQUEST, "전액 취소만 가능한 건입니다.", "P08"),
	ALREADY_EXIST_PAYMENT(HttpStatus.BAD_REQUEST, "이미 존재하는 결제입니다.", "P09"),
	EXTERNAL_PAYMENT_ERROR(HttpStatus.BAD_REQUEST, "결제 대행사 오류가 발생했습니다.", "P10"),

	INVALID_BANK_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 은행 코드입니다.", "P11"),
	INVALID_CARD_COMPANY_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 카드사 코드입니다.", "P12"),
	ALREADY_DONE_PAYMENT(HttpStatus.BAD_REQUEST, "이미 처리된 결제입니다.", "P13"),
	INVALID_PAYMENT_METHOD_DETAILS(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 수단 정보입니다.", "P14"),

	JSON_PARSE_ERROR(HttpStatus.BAD_REQUEST, "JSON 파싱 중 오류가 발생했습니다.", "T01"),
	INVALID_ADMISSION_TOKEN(HttpStatus.BAD_REQUEST, "입장 토큰이 유효하지 않습니다.", "T02"),
	EXPIRED_ADMISSION_TOKEN(HttpStatus.BAD_REQUEST, "입장 토큰이 만료되었습니다.", "T03"),
	ADMISSION_TOKEN_MISMATCH(HttpStatus.BAD_REQUEST, "입장 토큰 정보가 요청과 일치하지 않습니다.", "T04"),
	INVALID_SESSION_TOKEN(HttpStatus.BAD_REQUEST, "세션 토큰이 유효하지 않습니다.", "T05"),
	SESSION_TOKEN_MISMATCH(HttpStatus.BAD_REQUEST, "세션 토큰 정보가 요청과 일치하지 않습니다.", "T06"),
	ALREADY_SOLD_SEAT(HttpStatus.BAD_REQUEST, "판매된 좌석입니다.", "T07"),
	ALREADY_HOLD_SEAT(HttpStatus.BAD_REQUEST, "이미 선점된 좌석입니다.", "T08"),
	NOT_CORRECT_SEAT(HttpStatus.BAD_REQUEST, "잘못된 좌석 정보입니다.", "T08"),
	INVALID_SHOW_SCHEDULE(HttpStatus.BAD_REQUEST, "잘못된 공연 정보입니다.", "T09"),
	EXCEEDED_MAX_TICKET_COUNT(HttpStatus.BAD_REQUEST, "인당 최대 4매까지 예매 가능합니다.", "T10"),
	INVALID_HOLD_SEAT(HttpStatus.BAD_REQUEST, "타인이 점유한 좌석입니다.", "T11"),
	SUSPECTED_MACRO_ACTIVITY(HttpStatus.BAD_REQUEST, "매크로 의심 유저입니다.", "T12"),

	INVALID_RESERVATION_STATUS(HttpStatus.BAD_REQUEST, "유효하지 않은 예약 상태입니다.", "B01"),
	CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "관람일 당일에는 취소가 불가능합니다.", "B02"),
	INVALID_TICKET_ID(HttpStatus.BAD_REQUEST, "유효하지 않는 티켓 ID입니다.", "B03"),
	ALREADY_CANCELED_OR_COMPLETED(HttpStatus.BAD_REQUEST, "이미 취소됐거나 관람이 완료된 예매입니다.", "B04"),
	ALREADY_CANCELED_TICKET(HttpStatus.BAD_REQUEST, "이미 취소된 티켓입니다.", "B05"),

	NOT_FOUND_SHOW(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다.", "M01"),
	NOT_FOUND_ARTIST(HttpStatus.NOT_FOUND, "존재하지 않는 배우입니다.", "M02"),
	ALREADY_LIKED_ARTIST(HttpStatus.BAD_REQUEST, "이미 좋아요한 배우입니다.", "M03"),
	ALREADY_JOINED_ARTIST_MEMBERSHIP(HttpStatus.BAD_REQUEST, "이미 해당 아티스트의 멤버십에 가입되어 있습니다.", "M04"),
	NOT_FOUND_ARTIST_MEMBERSHIP(HttpStatus.NOT_FOUND, "가입된 아티스트 멤버십이 없습니다.", "M05"),
	MEMBERSHIP_PAYMENT_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "멤버십 결제가 아직 완료되지 않았습니다.", "M06"),

	ALREADY_EXIST_REVIEW(HttpStatus.BAD_REQUEST, "이미 유저가 리뷰를 작성했습니다.", "MR01"),
	NOT_CHARM_POINT(HttpStatus.BAD_REQUEST, "매력 포인트가 아닙니다.", "MR02"),
	NOT_EMOTION_POINT(HttpStatus.BAD_REQUEST, "감정 포인트가 아닙니다.", "MR03"),
	NOT_FOUND_REVIEW_USER(HttpStatus.BAD_REQUEST, "잘못된 리뷰 정보입니다.", "MR04"),

	EVENT_SERIALIZATION_FAILED(HttpStatus.BAD_REQUEST, "이벤트 직렬화 오류입니다.", "I01"),
	EVENT_PUBLISH_FAILED(HttpStatus.BAD_REQUEST, "이벤트 발행 실패입니다.", "I02"),
	EVENT_USER_SIGNED_UP_FAILED(HttpStatus.BAD_REQUEST, "유저 회원가입 이벤트 소비 실패입니다.", "I03"),
	EVENT_DESERIALIZATION_FAILED(HttpStatus.BAD_REQUEST, "이벤트 역직렬화 오류입니다.", "I04");

	private final HttpStatus status;
	private final String message;
	private final String code;

}

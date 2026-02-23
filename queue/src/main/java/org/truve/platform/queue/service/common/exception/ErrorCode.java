package org.truve.platform.queue.service.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	NOT_FOUND_USER(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
	INVALID_REQUEST_SHOW_ID(HttpStatus.BAD_REQUEST, "잘못된 공연입니다."),
	INVALID_REQUEST_USER_ID(HttpStatus.BAD_REQUEST, "잘못된 유저입니다."),
	QUEUE_ENTRY_NOT_FOUND(HttpStatus.BAD_REQUEST, "대기열에 입장하지 않은 유저입니다."),

	;

	private final HttpStatus status;
	private final String message;

}

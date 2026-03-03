package com.truve.platform.common.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

	private final ErrorCode errorCode;

	private final String message;

	private final String code;

	public CustomException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.message = errorCode.getMessage();
		this.code =  errorCode.getCode();
	}

	public CustomException(ErrorCode errorCode, String message, String code) {
		super(message);
		this.errorCode = errorCode;
		this.message = message;
		this.code = code;
	}

	public CustomException(ErrorCode errorCode, String message) {
		this(errorCode, message, errorCode.getCode());
	}

}

package com.truve.platform.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {

	private HttpStatus status;
	private String message;

	public static ResponseEntity<ErrorData> error(HttpStatus status, String message, String errorCode) {
		return ResponseEntity.status(status).body(ErrorData.of(status.series().name(), message, errorCode));
	}

	@Getter
	@AllArgsConstructor
	public static class ErrorData {

		private String errorType;
		private String message;
		private String code;

		public static ErrorData of(String errorType, String message, String code) {
			return new ErrorData(errorType, message, code);
		}
	}
}

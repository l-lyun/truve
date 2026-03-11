package com.truve.platform.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResult<T> {

	private String  code;
	private String message;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private T data;

	public static ApiResult<Void> ok() {
		return ApiResult.of("ok", "성공", null);
	}

	public static <T> ApiResult<T> ok(T data) {
		return ApiResult.of("ok", "성공", data);
	}

	public static <T> ApiResult<PageResponse<T>> ok(Page<T> page) {
		return ApiResult.of("ok", "성공", new PageResponse<>(page));
	}

	private static <T> ApiResult<T> of(String code, String message, T data) {
		return new ApiResult<T> (code, message, data);
	}
}

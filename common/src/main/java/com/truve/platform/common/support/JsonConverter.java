package com.truve.platform.common.support;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JsonConverter {

	private final ObjectMapper objectMapper;

	public <T> T convert(String payload, Class<T> clazz) {
		try {
			return objectMapper.readValue(payload, clazz);
		} catch (IOException e) {
			throw new CustomException(ErrorCode.EVENT_DESERIALIZATION_FAILED);
		}
	}
}

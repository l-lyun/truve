package com.truve.platform.auth.service.repository;

import java.time.Duration;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.auth.service.service.social.SocialRegistrationInfo;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.auth.service.support.RedisSupport;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SocialRegistrationRepository {

	private static final String SOCIAL_REGISTRATION_PREFIX = "social:registration:";
	private static final Duration SOCIAL_REGISTRATION_TTL = Duration.ofMinutes(30);

	private final RedisSupport redisSupport;
	private final ObjectMapper objectMapper;

	public void save(String registrationToken, SocialRegistrationInfo info) {
		try {
			String key = SOCIAL_REGISTRATION_PREFIX + registrationToken;
			String value = objectMapper.writeValueAsString(info);
			redisSupport.setValueWithTtl(key, value, SOCIAL_REGISTRATION_TTL);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.EVENT_SERIALIZATION_FAILED);
		}
	}

	public SocialRegistrationInfo find(String registrationToken) {
		try {
			String key = SOCIAL_REGISTRATION_PREFIX + registrationToken;
			String value = redisSupport.getValue(key);
			if (!StringUtils.hasText(value)) {
				return null;
			}
			return objectMapper.readValue(value, SocialRegistrationInfo.class);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.EVENT_DESERIALIZATION_FAILED);
		}
	}

	public void delete(String registrationToken) {
		String key = SOCIAL_REGISTRATION_PREFIX + registrationToken;
		redisSupport.delete(key);
	}
}

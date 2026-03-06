package org.truve.platform.ticketing.service.global.support;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisSupport {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;


	public void setValue(String key, String value) {
		redisTemplate.opsForValue().set(key, value);
	}

	public void setValueWithTtl(String key, String value, Duration duration) {
		redisTemplate.opsForValue().set(key, value, duration);
	}

	public String getValue(String key) {
		return redisTemplate.opsForValue().get(key);
	}

	public boolean delete(String key) {
		return Boolean.TRUE.equals(redisTemplate.delete(key));
	}

	public boolean consumeIfEquals(String key, String expectedValue) {
		String script =
			"local current = redis.call('GET', KEYS[1]) " +
				"if (not current) then return 0 end " +
				"if (current == ARGV[1]) then " +
				"redis.call('DEL', KEYS[1]) " +
				"return 1 " +
				"end " +
				"return 0";

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			Collections.singletonList(key),
			expectedValue
		);
		return Long.valueOf(1L).equals(result);
	}


	public void setJsonValue(String key, Object value) {
		try {
			String json = objectMapper.writeValueAsString(value);
			setValue(key, json);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.JSON_PARSE_ERROR);
		}
	}

	public void setJsonValueWithTtl(String key, Object value, Duration duration) {
		try {
			String json = objectMapper.writeValueAsString(value);
			setValueWithTtl(key, json, duration);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.JSON_PARSE_ERROR);
		}
	}

	public <T> T getJsonValue(String key, Class<T> type) {
		String json = getValue(key);
		if (json == null) return null;
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.JSON_PARSE_ERROR);
		}
	}

	public boolean setIfAbsent(String key, String value, Duration duration) {
		return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, duration));
	}

	public void zAdd(String key, String member, double score) {
		redisTemplate.opsForZSet().add(key, member, score);
	}

	public long zRemRangeByScore(String key, double minScore, double maxScore) {
		return redisTemplate.opsForZSet().removeRangeByScore(key, minScore, maxScore);
	}

	public boolean expireSeconds(String key, long ttl) {
		return Boolean.TRUE.equals(redisTemplate.expire(key, ttl, TimeUnit.SECONDS));
	}

	public long getTtlMillis(String key) {
		return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
	}
}

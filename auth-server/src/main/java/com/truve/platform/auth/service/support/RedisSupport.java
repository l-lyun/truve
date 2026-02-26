package com.truve.platform.auth.service.support;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisSupport {

	private final StringRedisTemplate redisTemplate;

	public void setValueWithTtl(String key, String value, Duration duration) {
		redisTemplate.opsForValue().set(key, value, duration);
	}

	public void setValue(String key, String value) {
		redisTemplate.opsForValue().set(key, value);
	}

	public String getValue(String key) {
		return redisTemplate.opsForValue().get(key);
	}

	public void expire(String key, Duration duration) {
		redisTemplate.expire(key, duration);
	}

	public void delete(String key) {
		redisTemplate.delete(key);
	}

}

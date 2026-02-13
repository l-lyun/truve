package com.truve.platform.auth.service.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private final StringRedisTemplate redisTemplate;
	private static final String PREFIX = "RT:";

	private String key(Long userId) {
		return PREFIX + userId.toString();
	}

	public void save(Long userId, String refreshToken, long expireMs) {
		redisTemplate.opsForValue()
			.set(key(userId), refreshToken, Duration.ofMillis(expireMs));
	}

	public boolean isSame(Long userId, String refreshToken) {
		String stored = redisTemplate.opsForValue().get(key(userId));
		return refreshToken.equals(stored);
	}

	public void delete(Long userId) {
		redisTemplate.delete(key(userId));
	}
}
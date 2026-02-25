package org.truve.platform.queue.service.queue.support;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisSupport {

	private final StringRedisTemplate redisTemplate;

	public void setValue(String key, String value) {
		redisTemplate.opsForValue().set(key, value);
	}

	public void setValueWithTtl(String key, String value, Duration duration) {
		redisTemplate.opsForValue().set(key, value, duration);
	}

	public void sAdd(String key, String value) {
		redisTemplate.opsForSet().add(key, value);
	}

	public Optional<String> getValue(String key) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(key));
	}

	public void zAddNx(String key, String member, long score) {
		redisTemplate.opsForZSet().addIfAbsent(key, member, score);
	}

	public Optional<Long> zRank(String key, String member) {
		return Optional.ofNullable(redisTemplate.opsForZSet().rank(key, member));
	}

	public String zSetPop(String key) {
		ZSetOperations.TypedTuple<String> tuple = redisTemplate.opsForZSet().popMin(key);
		if (tuple == null)
			return null;
		return tuple.getValue();
	}

	public boolean delete(String key) {
		return Boolean.TRUE.equals(redisTemplate.delete(key));
	}

	public Optional<Long> getExpire(String key) {
		return Optional.ofNullable(redisTemplate.getExpire(key));
	}

	public Set<String> sMembers(String key) {
		Set<String> members = redisTemplate.opsForSet().members(key);
		if (members == null || members.isEmpty()) {
			return Collections.emptySet();
		}
		return members;
	}

	public Optional<Long> zSetSize(String key) {
		return Optional.ofNullable(redisTemplate.opsForZSet().size(key));
	}

	public boolean setIfAbsent(String key, String value, Duration duration) {
		return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, duration));
	}

	public Optional<Long> zSetCount(String key, long minScore) {
		return Optional.ofNullable(redisTemplate.opsForZSet().count(key, minScore, Double.MAX_VALUE));
	}

}

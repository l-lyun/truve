package org.truve.platform.queue.service.queue.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

	private static final String WAIT_KEY_PREFIX = "queue:wait:";
	private static final String READY_KEY_PREFIX = "queue:ready:";
	private static final String SHOW_SET_KEY = "queue:shows";

	private final StringRedisTemplate redisTemplate;

	public void registerShow(String showId) {
		redisTemplate.opsForSet().add(SHOW_SET_KEY, showId);
	}

	public void enqueue(String showId, String userId, long enqueueTime) {
		redisTemplate.opsForZSet().addIfAbsent(waitKey(showId), userId, enqueueTime);
	}

	public Optional<Long> getRank(String showId, String userId) {
		var rank = redisTemplate.opsForZSet().rank(waitKey(showId), userId);
		return Optional.ofNullable(rank);
	}

	public List<String> popWaitingUsers(String showId, int count) {
		List<String> userIds = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ZSetOperations.TypedTuple<String> tuple = redisTemplate.opsForZSet().popMin(waitKey(showId));
			if (tuple == null || tuple.getValue() == null) {
				break;
			}
			userIds.add(tuple.getValue());
		}
		return userIds;
	}

	public void saveReadyToken(String showId, String userId, String token, long ttlSec) {
		redisTemplate.opsForValue().set(readyKey(showId, userId), token, Duration.ofSeconds(ttlSec));
	}

	public Optional<String> getReadyToken(String showId, String userId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(readyKey(showId, userId)));
	}

	public Optional<Long> getReadyTokenTtlSec(String showId, String userId) {
		Long ttl = redisTemplate.getExpire(readyKey(showId, userId));
		if (ttl == null || ttl < 0) {
			return Optional.empty();
		}
		return Optional.of(ttl);
	}

	public Set<String> getShows() {
		Set<String> shows = redisTemplate.opsForSet().members(SHOW_SET_KEY);
		return Objects.requireNonNullElse(shows, Set.of());
	}

	public Long getWaitingUserCount(String showId) {
		return redisTemplate.opsForZSet().size(waitKey(showId));
	}


	private static String waitKey(String showId) {
		return WAIT_KEY_PREFIX + showId;
	}

	private static String readyKey(String showId, String userId) {
		return READY_KEY_PREFIX + showId + ":" + userId;
	}
}

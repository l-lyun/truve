package org.truve.platform.queue.service.queue.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.truve.platform.queue.service.queue.support.RedisSupport;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

	private static final String WAIT_KEY_PREFIX = "queue:wait:";
	private static final String READY_KEY_PREFIX = "queue:ready:";
	private static final String SHOW_SET_KEY = "queue:shows";
	private static final String TICKET_ACTIVE_PREFIX = "ticket:active:";

	private final RedisSupport redisSupport;

	public void registerShow(String showId) {
		redisSupport.sAdd(SHOW_SET_KEY, showId);
	}

	public void enqueue(String showId, String userId, long enqueueTime) {
		redisSupport.zAddNx(waitKey(showId), userId, enqueueTime);
	}

	public Optional<Long> getRank(String showId, String userId) {
		return redisSupport.zRank(waitKey(showId), userId);
	}

	public List<String> popWaitingUsers(String showId, long count) {
		List<String> userIds = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String userId = redisSupport.zSetPop(waitKey(showId));
			if (userId == null) {
				break;
			}
			userIds.add(userId);
		}
		return userIds;
	}

	public void saveReadyToken(String showId, String userId, String token, long ttlSec) {
		redisSupport.setValueWithTtl(readyKey(showId, userId), token, Duration.ofSeconds(ttlSec));
	}

	public Optional<String> getReadyToken(String showId, String userId) {
		return redisSupport.getValue(readyKey(showId, userId));
	}

	public Optional<Long> getReadyTokenTtlSec(String showId, String userId) {
		Optional<Long> ttl = redisSupport.getExpire(readyKey(showId, userId));
		if (ttl.isEmpty() || ttl.get() < 0) {
			return Optional.empty();
		}
		return ttl;
	}

	public Set<String> getShows() {
		return redisSupport.sMembers(SHOW_SET_KEY);
	}

	public long getWaitingUserCount(String showId) {
		return redisSupport.zSetSize(waitKey(showId)).orElse(0L);
	}

	public long countActiveUsers(String showId, long minScore) {
		String key = TICKET_ACTIVE_PREFIX + showId;
		return redisSupport.zSetCount(key, minScore).orElse(0L);
	}

	private static String waitKey(String showId) {
		return WAIT_KEY_PREFIX + showId;
	}

	private static String readyKey(String showId, String userId) {
		return READY_KEY_PREFIX + showId + ":" + userId;
	}
}

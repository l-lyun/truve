package org.truve.platform.ticketing.service.repository;

import java.time.Duration;

import org.springframework.stereotype.Repository;
import org.truve.platform.ticketing.service.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.support.RedisSupport;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketingRedisRepository {

	private static final String SESSION_TOKEN_PREFIX = "ticket:session:";
	private static final String TICKET_ACTIVE_PERFORMANCE_USER_PREFIX = "ticket:active:";
	private static final String READY_KEY_PREFIX = "queue:ready:";

	private final RedisSupport redisSupport;

	public boolean consumeAdmissionToken(String showId, String userId, String admissionToken) {
		String key = READY_KEY_PREFIX + showId + ":" + userId;
		return redisSupport.consumeIfEquals(key, admissionToken);
	}

	public void saveSessionToken(String sessionToken, String userId, String showId, Duration ttl) {
		String key =  SESSION_TOKEN_PREFIX + sessionToken;
		SessionTicketValueDTO value = SessionTicketValueDTO.of(userId, showId);
		redisSupport.setJsonValueWithTtl(key, value, ttl);
	}

	public void addActiveTicketingUser(String showId, String sessionToken) {
		String key = TICKET_ACTIVE_PERFORMANCE_USER_PREFIX + showId;
		long nowMs =  System.currentTimeMillis();
		redisSupport.zAdd(key, sessionToken, nowMs);
	}

	public long getSessionTokenTtl(String sessionToken) {
		String key = SESSION_TOKEN_PREFIX + sessionToken;
		return redisSupport.getTtlMillis(key);
	}

	public SessionTicketValueDTO getSessionTokenValue(String sessionToken) {
		String key = SESSION_TOKEN_PREFIX + sessionToken;
		return redisSupport.getJsonValue(key, SessionTicketValueDTO.class);
	}

	public long removeInactiveTicketingUsers(String showId, long beforeMs) {
		String key = TICKET_ACTIVE_PERFORMANCE_USER_PREFIX + showId;
		return redisSupport.zRemRangeByScore(key, 0, beforeMs - 1);
	}

	public boolean refreshSessionTokenTtl(String sessionToken, long ttlSeconds) {
		String key = SESSION_TOKEN_PREFIX + sessionToken;
		return redisSupport.expireSeconds(key, ttlSeconds);
	}


}

package org.truve.platform.ticketing.service.schedule.repository;

import java.time.Duration;

import org.springframework.stereotype.Repository;
import org.truve.platform.ticketing.service.schedule.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.global.support.RedisSupport;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketingRedisRepository {

	private static final String SESSION_TOKEN_PREFIX = "ticket:session:";
	private static final String TICKET_ACTIVE_SHOW_USER_PREFIX = "ticket:active:";
	private static final String READY_KEY_PREFIX = "queue:ready:";
	private static final String SEAT_HOLD_KEY_PREFIX = "seat:hold:";

	private final RedisSupport redisSupport;

	public boolean consumeAdmissionToken(Long showId, Long userId, String admissionToken) {
		String key = READY_KEY_PREFIX + showId + ":" + userId;
		return redisSupport.consumeIfEquals(key, admissionToken);
	}

	public void saveSessionToken(String sessionToken, Long userId, Long showId, Duration ttl) {
		String key =  SESSION_TOKEN_PREFIX + sessionToken;
		SessionTicketValueDTO value = SessionTicketValueDTO.of(userId, showId);
		redisSupport.setJsonValueWithTtl(key, value, ttl);
	}

	public void addActiveTicketingUser(Long showId, String sessionToken) {
		String key = TICKET_ACTIVE_SHOW_USER_PREFIX + showId;
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

	public long removeInactiveTicketingUsers(Long showId, long beforeMs) {
		String key = TICKET_ACTIVE_SHOW_USER_PREFIX + showId;
		return redisSupport.zRemRangeByScore(key, 0, beforeMs - 1);
	}

	public boolean refreshSessionTokenTtl(String sessionToken, long ttlSeconds) {
		String key = SESSION_TOKEN_PREFIX + sessionToken;
		return redisSupport.expireSeconds(key, ttlSeconds);
	}

	public boolean tryHoldSeat(Long showScheduleId, Long seatId, String sessionToken) {
		String key = SEAT_HOLD_KEY_PREFIX + showScheduleId + ":" + seatId;
		// TODO: 좌석 점유 시간 기획측과 논의
		return redisSupport.setIfAbsent(key, sessionToken, Duration.ofMinutes(10));
	}

	public String getHoldSeatSessionToken(Long showScheduleId, Long seatId) {
		String key = SEAT_HOLD_KEY_PREFIX + showScheduleId + ":" + seatId;
		return redisSupport.getValue(key);
	}


}

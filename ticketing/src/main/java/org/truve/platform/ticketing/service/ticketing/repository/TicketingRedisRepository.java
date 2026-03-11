package org.truve.platform.ticketing.service.ticketing.repository;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.truve.platform.ticketing.service.ticketing.dto.SessionTicketValueDTO;
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

	public boolean consumeAdmissionToken(Long showId, UUID userId, String admissionToken) {
		return redisSupport.consumeIfEquals(readyUserKey(showId, userId), admissionToken);
	}

	public void saveSessionToken(String sessionToken, UUID userId, Long showId, Duration ttl) {
		SessionTicketValueDTO value = SessionTicketValueDTO.of(userId, showId);
		redisSupport.setJsonValueWithTtl(sessionTokenKey(sessionToken), value, ttl);
	}

	public void addActiveTicketingUser(Long showId, String sessionToken) {
		long nowMs =  System.currentTimeMillis();
		redisSupport.zAdd(activeTicketingUserKey(showId), sessionToken, nowMs);
	}

	public long getSessionTokenTtl(String sessionToken) {
		return redisSupport.getTtlMillis(sessionTokenKey(sessionToken));
	}

	public SessionTicketValueDTO getSessionTokenValue(String sessionToken) {
		return redisSupport.getJsonValue(sessionTokenKey(sessionToken), SessionTicketValueDTO.class);
	}

	public long removeInactiveTicketingUsers(Long showId, long beforeMs) {
		return redisSupport.zRemRangeByScore(activeTicketingUserKey(showId), 0, beforeMs - 1);
	}

	public boolean refreshSessionTokenTtl(String sessionToken, long ttlSeconds) {
		return redisSupport.expireSeconds(sessionTokenKey(sessionToken), ttlSeconds);
	}

	public boolean tryHoldSeat(Long showScheduleId, Long seatId, String sessionToken) {
		// TODO: 좌석 점유 시간 기획측과 논의
		return redisSupport.setIfAbsent(seatHoldKey(showScheduleId, seatId), sessionToken, Duration.ofMinutes(10));
	}

	public boolean deleteHoldSeat(Long showScheduleId, Long seatId) {
		return redisSupport.delete(seatHoldKey(showScheduleId, seatId));
	}

	public String getHoldSeatSessionToken(Long showScheduleId, Long seatId) {
		return redisSupport.getValue(seatHoldKey(showScheduleId, seatId));
	}

	private String seatHoldKey(Long showScheduleId, Long seatId) {
		return SEAT_HOLD_KEY_PREFIX + showScheduleId + ":" + seatId;
	}

	private String sessionTokenKey(String sessionToken) {
		return SESSION_TOKEN_PREFIX + sessionToken;
	}

	private String activeTicketingUserKey(Long showId) {
		return TICKET_ACTIVE_SHOW_USER_PREFIX + showId;
	}

	private String readyUserKey(Long showId, UUID userId) {
		return READY_KEY_PREFIX + showId + ":" + userId;
	}

}

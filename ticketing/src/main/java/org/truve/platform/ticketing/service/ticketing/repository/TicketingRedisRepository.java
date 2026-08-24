package org.truve.platform.ticketing.service.ticketing.repository;

import java.time.Duration;
import java.util.List;
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
	private static final String BLOCKED_TICKET_PREFIX = "blocked_ticket:";


	private final RedisSupport redisSupport;

	public boolean consumeAdmissionToken(Long showId, UUID userId, String admissionToken) {
		return redisSupport.consumeIfEquals(readyUserKey(showId, userId), admissionToken);
	}

	public void saveSessionToken(String sessionToken, UUID userId, Long showScheduleId, Duration ttl) {
		SessionTicketValueDTO value = SessionTicketValueDTO.of(userId, showScheduleId);
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

	public boolean tryHoldSeat(Long showScheduleId, Long scheduledSeatId, String sessionToken) {
		// TODO: 좌석 점유 시간 기획측과 논의
		return redisSupport.setIfAbsent(seatHoldKey(showScheduleId, scheduledSeatId), sessionToken, Duration.ofMinutes(10));
	}

	public boolean deleteHoldSeat(Long showScheduleId, Long scheduledSeatId, String sessionToken) {
		return redisSupport.consumeIfEquals(seatHoldKey(showScheduleId, scheduledSeatId), sessionToken);
	}

	public String getHoldSeatSessionToken(Long showScheduleId, Long scheduledSeatId) {
		return redisSupport.getValue(seatHoldKey(showScheduleId, scheduledSeatId));
	}

	public boolean claimHoldSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String claimValue
	) {
		return redisSupport.replaceAllIfEquals(
			seatHoldKeys(showScheduleId, scheduledSeatIds),
			sessionToken,
			claimValue
		);
	}

	public boolean releaseClaimedSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String claimValue
	) {
		return redisSupport.deleteAllIfEquals(seatHoldKeys(showScheduleId, scheduledSeatIds), claimValue);
	}

	public boolean restoreClaimedSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String claimValue,
		String sessionToken
	) {
		return redisSupport.replaceEachIfEquals(
			seatHoldKeys(showScheduleId, scheduledSeatIds),
			claimValue,
			sessionToken
		);
	}

	public String validateMacro(String sessionTicket) {
		String key = secureKey(sessionTicket);
		return redisSupport.getValue(key);
	}

	public void expireSessionToken(String sessionToken) {
		String key = sessionTokenKey(sessionToken);
		redisSupport.expireSeconds(key, 0);
	}

	public void exitTicketing(Long showId, String sessionToken) {
		String key = activeTicketingUserKey(showId);
		redisSupport.zRem(key, sessionToken);

	}

	private String seatHoldKey(Long showScheduleId, Long scheduledSeatId) {
		return SEAT_HOLD_KEY_PREFIX + showScheduleId + ":" + scheduledSeatId;
	}

	private List<String> seatHoldKeys(Long showScheduleId, List<Long> scheduledSeatIds) {
		return scheduledSeatIds.stream()
			.map(scheduledSeatId -> seatHoldKey(showScheduleId, scheduledSeatId))
			.toList();
	}

	private String secureKey(String sessionTicket) {
		return BLOCKED_TICKET_PREFIX + sessionTicket;
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

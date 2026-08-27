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
	private static final String SEAT_HOLD_LOCK_KEY_PREFIX = "seat:hold:lock:";
	private static final String SESSION_HELD_SEATS_KEY_PREFIX = "seat:holds:";
	private static final String SEAT_HOLD_META_KEY_PREFIX = "seat:hold:meta:";
	private static final String BLOCKED_TICKET_PREFIX = "blocked_ticket:";
	private static final Duration SEAT_HOLD_TTL = Duration.ofMinutes(10);
	private static final Duration SESSION_HELD_SEATS_TTL = Duration.ofMinutes(11);
	private static final Duration SEAT_HOLD_LOCK_TTL = Duration.ofSeconds(10);


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

	public boolean tryLockSeatHold(UUID userId, Long showScheduleId, String lockToken) {
		return redisSupport.setIfAbsent(seatHoldLockKey(showScheduleId, userId), lockToken, SEAT_HOLD_LOCK_TTL);
	}

	public boolean unlockSeatHold(UUID userId, Long showScheduleId, String lockToken) {
		return redisSupport.consumeIfEquals(seatHoldLockKey(showScheduleId, userId), lockToken);
	}

	public SeatHoldResult holdSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		int maxSeatCount
	) {
		long result = redisSupport.holdSeatsWithLimit(
			seatHoldKeysWithSessionSet(showScheduleId, scheduledSeatIds, sessionToken),
			scheduledSeatIds,
			sessionToken,
			seatHoldKeyPrefix(showScheduleId),
			bookingClaimPrefix(sessionToken),
			SEAT_HOLD_TTL,
			SESSION_HELD_SEATS_TTL,
			maxSeatCount
		);
		return SeatHoldResult.from(result);
	}

	public SeatHoldResult holdSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String holdId,
		int maxSeatCount
	) {
		long result = redisSupport.holdSeatLeasesWithLimit(
			seatHoldLeaseKeys(showScheduleId, scheduledSeatIds, sessionToken, holdId),
			scheduledSeatIds,
			sessionToken,
			holdId,
			seatHoldKeyPrefix(showScheduleId),
			SEAT_HOLD_META_KEY_PREFIX,
			SEAT_HOLD_TTL,
			SESSION_HELD_SEATS_TTL,
			maxSeatCount
		);
		return SeatHoldResult.fromLease(result);
	}

	public boolean compensateNewlyHeldSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String holdId
	) {
		return redisSupport.compensateNewlyHeldSeatLeases(
			seatHoldLeaseKeys(showScheduleId, scheduledSeatIds, sessionToken, holdId),
			scheduledSeatIds,
			sessionToken,
			holdId
		);
	}

	public boolean releaseHeldSeats(Long showScheduleId, List<Long> scheduledSeatIds, String sessionToken) {
		return redisSupport.releaseHeldSeats(
			seatHoldKeysWithSessionSet(showScheduleId, scheduledSeatIds, sessionToken),
			scheduledSeatIds,
			sessionToken
		);
	}

	public long releaseSessionHeldSeats(Long showScheduleId, String sessionToken) {
		return redisSupport.releaseSessionHeldSeats(
			sessionHeldSeatsKey(showScheduleId, sessionToken),
			seatHoldKeyPrefix(showScheduleId),
			sessionToken
		);
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
		return redisSupport.claimHeldSeats(
			seatHoldKeysWithSessionSet(showScheduleId, scheduledSeatIds, sessionToken),
			scheduledSeatIds,
			sessionToken,
			claimValue
		);
	}

	public boolean releaseClaimedSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String claimValue
	) {
		return redisSupport.releaseClaimedSeats(
			seatHoldKeysWithSessionSet(showScheduleId, scheduledSeatIds, sessionToken),
			scheduledSeatIds,
			claimValue
		);
	}

	public boolean restoreClaimedSeats(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String claimValue,
		String sessionToken
	) {
		return redisSupport.restoreClaimedSeats(
			seatHoldKeysWithSessionSet(showScheduleId, scheduledSeatIds, sessionToken),
			scheduledSeatIds,
			claimValue,
			sessionToken,
			SESSION_HELD_SEATS_TTL
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
		return seatHoldKeyPrefix(showScheduleId) + scheduledSeatId;
	}

	private String bookingClaimPrefix(String sessionToken) {
		return "booking:" + sessionToken + ":";
	}

	private String seatHoldKeyPrefix(Long showScheduleId) {
		return SEAT_HOLD_KEY_PREFIX + showScheduleId + ":";
	}

	private List<String> seatHoldKeys(Long showScheduleId, List<Long> scheduledSeatIds) {
		return scheduledSeatIds.stream()
			.map(scheduledSeatId -> seatHoldKey(showScheduleId, scheduledSeatId))
			.toList();
	}

	private List<String> seatHoldKeysWithSessionSet(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken
	) {
		List<String> keys = new java.util.ArrayList<>();
		keys.add(sessionHeldSeatsKey(showScheduleId, sessionToken));
		keys.addAll(seatHoldKeys(showScheduleId, scheduledSeatIds));
		return keys;
	}

	private List<String> seatHoldLeaseKeys(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String holdId
	) {
		List<String> keys = new java.util.ArrayList<>();
		keys.add(sessionHeldSeatsKey(showScheduleId, sessionToken));
		keys.add(holdMetaKey(holdId));
		keys.addAll(seatHoldKeys(showScheduleId, scheduledSeatIds));
		return keys;
	}

	private String seatHoldLockKey(Long showScheduleId, UUID userId) {
		return SEAT_HOLD_LOCK_KEY_PREFIX + showScheduleId + ":" + userId;
	}

	private String sessionHeldSeatsKey(Long showScheduleId, String sessionToken) {
		return SESSION_HELD_SEATS_KEY_PREFIX + showScheduleId + ":" + sessionToken;
	}

	private String holdMetaKey(String holdId) {
		return SEAT_HOLD_META_KEY_PREFIX + holdId;
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

	public enum SeatHoldResult {
		SUCCESS,
		NEWLY_ACQUIRED,
		ALREADY_OWNED,
		CONFLICT,
		LIMIT_EXCEEDED;

		private static SeatHoldResult from(long result) {
			if (result == 1L) {
				return SUCCESS;
			}
			if (result == -1L) {
				return LIMIT_EXCEEDED;
			}
			return CONFLICT;
		}

		private static SeatHoldResult fromLease(long result) {
			if (result == 1L) {
				return NEWLY_ACQUIRED;
			}
			if (result == 2L) {
				return ALREADY_OWNED;
			}
			if (result == -1L) {
				return LIMIT_EXCEEDED;
			}
			return CONFLICT;
		}
	}

}

package org.truve.platform.ticketing.service.ticketing.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.ticketing.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatHoldService {
	private static final String BOOKING_CLAIM_FORMAT = "booking:%s:%s";

	private final TicketingRedisRepository ticketingRedisRepository;

	public void validateSession(UUID userId, Long showScheduleId, String sessionToken) {
		Preconditions.validate(sessionToken != null && !sessionToken.isBlank(), ErrorCode.INVALID_SESSION_TOKEN);

		SessionTicketValueDTO session = ticketingRedisRepository.getSessionTokenValue(sessionToken);
		Preconditions.validate(session != null, ErrorCode.INVALID_SESSION_TOKEN);
		Preconditions.validate(userId.equals(session.getUserId()), ErrorCode.SESSION_TOKEN_MISMATCH);
		Preconditions.validate(showScheduleId.equals(session.getShowScheduleId()), ErrorCode.SESSION_TOKEN_MISMATCH);
	}

	public SeatClaim claim(
		Long showScheduleId,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String reservationNumber
	) {
		String claimValue = BOOKING_CLAIM_FORMAT.formatted(reservationNumber, UUID.randomUUID());
		boolean claimed = ticketingRedisRepository.claimHoldSeats(
			showScheduleId,
			scheduledSeatIds,
			sessionToken,
			claimValue
		);
		Preconditions.validate(claimed, ErrorCode.INVALID_BOOKING_SEAT_HOLD);
		return new SeatClaim(showScheduleId, List.copyOf(scheduledSeatIds), claimValue);
	}

	public void release(SeatClaim claim) {
		ticketingRedisRepository.releaseClaimedSeats(
			claim.showScheduleId(),
			claim.scheduledSeatIds(),
			claim.claimValue()
		);
	}

	public record SeatClaim(Long showScheduleId, List<Long> scheduledSeatIds, String claimValue) {
	}
}

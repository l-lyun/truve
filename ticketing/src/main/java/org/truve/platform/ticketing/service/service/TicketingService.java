package org.truve.platform.ticketing.service.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.domain.entity.ShowScheduleSeat;
import org.truve.platform.ticketing.service.dto.AdmissionTokenClaimsDTO;
import org.truve.platform.ticketing.service.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.dto.TicketingResponse;
import org.truve.platform.ticketing.service.config.TicketingProperties;
import org.truve.platform.ticketing.service.jwt.AdmissionTokenService;
import org.truve.platform.ticketing.service.repository.ShowScheduleSeatRepository;
import org.truve.platform.ticketing.service.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketingService {

	private final TicketingRedisRepository ticketingRedisRepository;
	private final AdmissionTokenService admissionTokenService;
	private final TicketingProperties ticketingProperties;
	private final ShowScheduleSeatRepository showScheduleSeatRepository;

	public TicketingResponse.Enter enter(Long showScheduleId, Long userId, String admissionToken) {
		AdmissionTokenClaimsDTO claims = admissionTokenService.parseAdmissionToken(admissionToken, showScheduleId, userId);

		boolean consumedAdmissionToken = ticketingRedisRepository.consumeAdmissionToken(claims.getShowId(), claims.getUserId(), admissionToken);
		Preconditions.validate(consumedAdmissionToken, ErrorCode.INVALID_ADMISSION_TOKEN);

		String sessionToken = UUID.randomUUID().toString();

		// TODO: 만료시간 기획측과 논의
		ticketingRedisRepository.saveSessionToken(sessionToken, userId, showScheduleId, Duration.ofMinutes(5));
		ticketingRedisRepository.addActiveTicketingUser(showScheduleId, sessionToken);
		long sessionTokenTtl = ticketingRedisRepository.getSessionTokenTtl(sessionToken);

		return new TicketingResponse.Enter(sessionToken, sessionTokenTtl);
	}

	public void heartbeat(Long showScheduleId, Long userId, String sessionToken) {

		isCorrectSessionToken(showScheduleId, userId, sessionToken);

		ticketingRedisRepository.addActiveTicketingUser(showScheduleId, sessionToken);
		long nowMs = System.currentTimeMillis();
		long activeWindowMs = ticketingProperties.getActiveWindowMs();
		ticketingRedisRepository.removeInactiveTicketingUsers(showScheduleId, nowMs - activeWindowMs);

		boolean extended = ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, ticketingProperties.getSessionTtlSec());
		Preconditions.validate(extended, ErrorCode.INVALID_SESSION_TOKEN);
	}

	public void holdSeat(Long showScheduleId, Long userId, String sessionToken, Long showScheduleSeatId) {
		heartbeat(showScheduleId, userId, sessionToken);

		ShowScheduleSeat seat = showScheduleSeatRepository.findById(showScheduleSeatId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_CORRECT_SEAT));

		Preconditions.validate(
			seat.isAvailable(),
			ErrorCode.ALREADY_SOLD_SEAT
		);

		Preconditions.validate(
			seat.getShowScheduleId().equals(showScheduleId),
			ErrorCode.NOT_CORRECT_SEAT
		);

		boolean tryHoldSeatResult = ticketingRedisRepository.tryHoldSeat(showScheduleId, seat.getSeatId(), sessionToken);

		if (!tryHoldSeatResult) {
			String savesSessionToken = ticketingRedisRepository.getHoldSeatSessionToken(showScheduleId, seat.getSeatId());
			Preconditions.validate(sessionToken.equals(savesSessionToken), ErrorCode.ALREADY_HOLD_SEAT);
		}
	}

	private void isCorrectSessionToken(Long showScheduleId, Long userId, String sessionToken) {
		Preconditions.validate(sessionToken != null && !sessionToken.isBlank(), ErrorCode.INVALID_SESSION_TOKEN);

		SessionTicketValueDTO sessionValue = ticketingRedisRepository.getSessionTokenValue(sessionToken);

		Preconditions.validate(sessionValue != null, ErrorCode.INVALID_SESSION_TOKEN);
		Preconditions.validate(userId.equals(sessionValue.getUserId()), ErrorCode.SESSION_TOKEN_MISMATCH);
		Preconditions.validate(showScheduleId.equals(sessionValue.getShowId()), ErrorCode.SESSION_TOKEN_MISMATCH);
	}

}

package org.truve.platform.ticketing.service.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.dto.AdmissionTokenClaimsDTO;
import org.truve.platform.ticketing.service.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.dto.TicketingResponse;
import org.truve.platform.ticketing.service.config.TicketingProperties;
import org.truve.platform.ticketing.service.jwt.AdmissionTokenService;
import org.truve.platform.ticketing.service.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketingService {

	private final TicketingRedisRepository ticketingRedisRepository;
	private final AdmissionTokenService admissionTokenService;
	private final TicketingProperties ticketingProperties;

	public TicketingResponse.Enter enter(String showId, String userId, String admissionToken) {
		AdmissionTokenClaimsDTO claims = admissionTokenService.parseAdmissionToken(admissionToken, showId, userId);

		boolean consumedAdmissionToken = ticketingRedisRepository.consumeAdmissionToken(claims.getShowId(), claims.getUserId(), admissionToken);
		Preconditions.validate(consumedAdmissionToken, ErrorCode.INVALID_ADMISSION_TOKEN);

		String sessionToken = UUID.randomUUID().toString();

		// TODO: 만료시간 기획측과 논의
		ticketingRedisRepository.saveSessionToken(sessionToken, userId, showId, Duration.ofMinutes(5));
		ticketingRedisRepository.addActiveTicketingUser(showId, sessionToken);
		long sessionTokenTtl = ticketingRedisRepository.getSessionTokenTtl(sessionToken);

		return new TicketingResponse.Enter(sessionToken, sessionTokenTtl);
	}

	public void heartbeat(String showId, String userId, String sessionToken) {
		Preconditions.validate(sessionToken != null && !sessionToken.isBlank(), ErrorCode.INVALID_SESSION_TOKEN);

		SessionTicketValueDTO sessionValue = ticketingRedisRepository.getSessionTokenValue(sessionToken);

		Preconditions.validate(sessionValue != null, ErrorCode.INVALID_SESSION_TOKEN);
		Preconditions.validate(userId.equals(sessionValue.getUserId()), ErrorCode.SESSION_TOKEN_MISMATCH);
		Preconditions.validate(showId.equals(sessionValue.getShowId()), ErrorCode.SESSION_TOKEN_MISMATCH);

		ticketingRedisRepository.addActiveTicketingUser(showId, sessionToken);
		long nowMs = System.currentTimeMillis();
		long activeWindowMs = ticketingProperties.getActiveWindowMs();
		ticketingRedisRepository.removeInactiveTicketingUsers(showId, nowMs - activeWindowMs);

		boolean extended = ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, ticketingProperties.getSessionTtlSec());
		Preconditions.validate(extended, ErrorCode.INVALID_SESSION_TOKEN);

	}
}

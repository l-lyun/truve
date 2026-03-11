package org.truve.platform.ticketing.service.ticketing.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.dto.AdmissionTokenClaimsDTO;
import org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto;
import org.truve.platform.ticketing.service.ticketing.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.config.TicketingProperties;
import org.truve.platform.ticketing.service.global.jwt.AdmissionTokenService;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

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
	private final ScheduledSeatRepository scheduledSeatRepository;
	private final ShowScheduledRepository showScheduledRepository;

	public TicketingResponse.Enter enter(Long showScheduleId, UUID userId, String admissionToken) {
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

	public void heartbeat(Long showScheduleId, UUID userId, String sessionToken) {

		isCorrectSessionToken(showScheduleId, userId, sessionToken);

		ticketingRedisRepository.addActiveTicketingUser(showScheduleId, sessionToken);
		long nowMs = System.currentTimeMillis();
		long activeWindowMs = ticketingProperties.getActiveWindowMs();
		ticketingRedisRepository.removeInactiveTicketingUsers(showScheduleId, nowMs - activeWindowMs);

		boolean extended = ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, ticketingProperties.getSessionTtlSec());
		Preconditions.validate(extended, ErrorCode.INVALID_SESSION_TOKEN);
	}

	public void holdSeat(Long showScheduleId, UUID userId, String sessionToken, List<Long> seatIds) {
		heartbeat(showScheduleId, userId, sessionToken);

		Preconditions.validate(seatIds.size() <= 4, ErrorCode.EXCEEDED_MAX_TICKET_COUNT);

		ShowScheduled showScheduled = showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

		List<ScheduledSeat> seats = scheduledSeatRepository.findAllById(seatIds);

		Preconditions.validate(seatIds.size() == seats.size(), ErrorCode.NOT_CORRECT_SEAT);

		for(ScheduledSeat seat: seats) {

			Preconditions.validate(
				showScheduled.getId().equals(seat.getShowScheduleId()),
				ErrorCode.NOT_CORRECT_SEAT
			);

			Preconditions.validate(
				seat.isAvailable(),
				ErrorCode.ALREADY_SOLD_SEAT
			);

			Preconditions.validate(
				seat.getShowScheduleId().equals(showScheduleId),
				ErrorCode.NOT_CORRECT_SEAT
			);

			Long seatId = seat.getSeat().getId();
			boolean tryHoldSeatLock = ticketingRedisRepository.tryHoldSeat(showScheduleId, seatId, sessionToken);

			if (!tryHoldSeatLock) {
				String savesSessionToken = ticketingRedisRepository.getHoldSeatSessionToken(showScheduleId, seatId);
				Preconditions.validate(sessionToken.equals(savesSessionToken), ErrorCode.ALREADY_HOLD_SEAT);
			}
		}

	}

	public void cancelHoldSeat(Long showScheduleId, UUID userId, String sessionToken,List<Long> seatIds) {

		heartbeat(showScheduleId, userId, sessionToken);

		for (Long seatId : seatIds) {
			String savedSessionToken = ticketingRedisRepository.getHoldSeatSessionToken(showScheduleId, seatId);
			Preconditions.validate(sessionToken.equals(savedSessionToken), ErrorCode.INVALID_HOLD_SEAT);
			ticketingRedisRepository.deleteHoldSeat(showScheduleId, seatId);
		}
	}

	public TicketingResponse.Show getShow(UUID userId, Long showScheduleId, String sessionToken) {
		heartbeat(showScheduleId, userId, sessionToken);

		ShowScheduled schedule = showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

		return TicketingResponse.Show.of(schedule.getTitle(), schedule.getTitle(), schedule.getStartAt());
	}

	public TicketingResponse.Seats getSeats(Long showScheduleId, UUID userId, String sessionToken) {
		heartbeat(showScheduleId, userId, sessionToken);

		showScheduledRepository.findById(showScheduleId).orElseThrow(
			() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE)
		);

		List<SeatSectionsDto> flatSeats = scheduledSeatRepository.findSeatSectionByScheduledSeatId(showScheduleId);

		return TicketingResponse.Seats.from(flatSeats);
	}


	private void isCorrectSessionToken(Long showScheduleId, UUID userId, String sessionToken) {
		Preconditions.validate(sessionToken != null && !sessionToken.isBlank(), ErrorCode.INVALID_SESSION_TOKEN);


		SessionTicketValueDTO sessionValue = ticketingRedisRepository.getSessionTokenValue(sessionToken);

		Preconditions.validate(sessionValue != null, ErrorCode.INVALID_SESSION_TOKEN);
		Preconditions.validate(userId.equals(sessionValue.getUserId()), ErrorCode.SESSION_TOKEN_MISMATCH);
		Preconditions.validate(showScheduleId.equals(sessionValue.getShowId()), ErrorCode.SESSION_TOKEN_MISMATCH);
	}

}

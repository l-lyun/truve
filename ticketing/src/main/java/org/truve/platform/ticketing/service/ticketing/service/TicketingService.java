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
	private final TicketingSecurityService  ticketingSecurityService;

	public TicketingResponse.Enter enter(Long showScheduleId, UUID userId, String admissionToken) {
		AdmissionTokenClaimsDTO claims = admissionTokenService.parseAdmissionToken(admissionToken, showScheduleId, userId);
		boolean consumedAdmissionToken = ticketingRedisRepository.consumeAdmissionToken(
			claims.getShowId(), claims.getUserId(), admissionToken
		);
		Preconditions.validate(consumedAdmissionToken, ErrorCode.INVALID_ADMISSION_TOKEN);

		String sessionToken = UUID.randomUUID().toString();

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

		boolean extended = ticketingRedisRepository.refreshSessionTokenTtl(
			sessionToken, ticketingProperties.getSessionTtlSec()
		);
		Preconditions.validate(extended, ErrorCode.INVALID_SESSION_TOKEN);
	}

	public void holdSeat(Long showScheduleId, UUID userId, String sessionToken, List<Long> scheduledSeatIds) {
		ticketingSecurityService.findMacro(sessionToken);
		heartbeat(showScheduleId, userId, sessionToken);

		Preconditions.validate(scheduledSeatIds.size() <= 4, ErrorCode.EXCEEDED_MAX_TICKET_COUNT);

		ShowScheduled showScheduled = showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllById(scheduledSeatIds);

		Preconditions.validate(scheduledSeatIds.size() == scheduledSeats.size(), ErrorCode.NOT_CORRECT_SEAT);

		for(ScheduledSeat seat: scheduledSeats) {

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

			Long scheduledSeatId = seat.getId();
			boolean tryHoldSeatLock = ticketingRedisRepository.tryHoldSeat(showScheduleId, scheduledSeatId, sessionToken);

			if (!tryHoldSeatLock) {
				String savesSessionToken = ticketingRedisRepository.getHoldSeatSessionToken(showScheduleId, scheduledSeatId);
				Preconditions.validate(sessionToken.equals(savesSessionToken), ErrorCode.ALREADY_HOLD_SEAT);
			}
		}

	}

	public void cancelHoldSeat(Long showScheduleId, UUID userId, String sessionToken, List<Long> scheduledSeatIds) {

		heartbeat(showScheduleId, userId, sessionToken);

		List<ScheduledSeat> seats = scheduledSeatRepository.findAllById(scheduledSeatIds);

		Preconditions.validate(scheduledSeatIds.size() == seats.size(), ErrorCode.NOT_CORRECT_SEAT);

		for (ScheduledSeat seat : seats) {
			Preconditions.validate(
				seat.getShowScheduleId().equals(showScheduleId),
				ErrorCode.NOT_CORRECT_SEAT
			);

			Long scheduledSeatId = seat.getId();
			boolean deleted = ticketingRedisRepository.deleteHoldSeat(showScheduleId, scheduledSeatId, sessionToken);
			Preconditions.validate(deleted, ErrorCode.INVALID_HOLD_SEAT);
		}
	}

	public TicketingResponse.Show getShow(UUID userId, Long showScheduleId, String sessionToken) {
		heartbeat(showScheduleId, userId, sessionToken);

		ShowScheduled schedule = showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

		return TicketingResponse.Show.of(schedule.getTitle(), schedule.getVenueName(), schedule.getStartAt());
	}

	public TicketingResponse.Seats getSeats(Long showScheduleId, UUID userId, String sessionToken) {
		heartbeat(showScheduleId, userId, sessionToken);

		showScheduledRepository.findById(showScheduleId).orElseThrow(
			() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE)
		);

		List<SeatSectionsDto> flatSeats = scheduledSeatRepository.findSeatSectionByScheduledSeatId(showScheduleId);

		return TicketingResponse.Seats.from(flatSeats);
	}

	public void exitTicketing(Long showScheduleId, UUID userId, String sessionToken) {
		isCorrectSessionToken(showScheduleId, userId, sessionToken);
		ticketingRedisRepository.expireSessionToken(sessionToken);
		ticketingRedisRepository.exitTicketing(showScheduleId, sessionToken);

		// TODO: 현재 프론트 로직이라면 선점한 좌석 만료가 필요함
	}

	private void isCorrectSessionToken(Long showScheduleId, UUID userId, String sessionToken) {
		Preconditions.validate(sessionToken != null && !sessionToken.isBlank(), ErrorCode.INVALID_SESSION_TOKEN);


		SessionTicketValueDTO sessionValue = ticketingRedisRepository.getSessionTokenValue(sessionToken);

		Preconditions.validate(sessionValue != null, ErrorCode.INVALID_SESSION_TOKEN);
		Preconditions.validate(userId.equals(sessionValue.getUserId()), ErrorCode.SESSION_TOKEN_MISMATCH);
		Preconditions.validate(showScheduleId.equals(sessionValue.getShowScheduleId()), ErrorCode.SESSION_TOKEN_MISMATCH);
	}

}

package org.truve.platform.ticketing.service.ticketing.service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository.SeatHoldResult;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketingService {
	private static final int MAX_HOLD_SEAT_COUNT = 4;

	private final TicketingRedisRepository ticketingRedisRepository;
	private final AdmissionTokenService admissionTokenService;
	private final TicketingProperties ticketingProperties;
	private final ScheduledSeatRepository scheduledSeatRepository;
	private final ShowScheduledRepository showScheduledRepository;
	private final TicketingSecurityService  ticketingSecurityService;
	private final SeatHoldLockService seatHoldLockService;

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
		validateSeatIds(scheduledSeatIds);
		List<Long> sortedSeatIds = scheduledSeatIds.stream().sorted().toList();

		ShowScheduled showScheduled = showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllById(sortedSeatIds);

		Preconditions.validate(sortedSeatIds.size() == scheduledSeats.size(), ErrorCode.NOT_CORRECT_SEAT);

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

		}

		SeatHoldLockService.SeatHoldLock lock = seatHoldLockService.acquire(userId, showScheduleId);
		try {
			SeatHoldResult result = ticketingRedisRepository.holdSeats(
				showScheduleId,
				sortedSeatIds,
				sessionToken,
				MAX_HOLD_SEAT_COUNT
			);
			Preconditions.validate(result != SeatHoldResult.LIMIT_EXCEEDED, ErrorCode.EXCEEDED_MAX_TICKET_COUNT);
			Preconditions.validate(result == SeatHoldResult.SUCCESS, ErrorCode.ALREADY_HOLD_SEAT);
		} finally {
			releaseSeatHoldLock(lock);
		}
	}

	public void cancelHoldSeat(Long showScheduleId, UUID userId, String sessionToken, List<Long> scheduledSeatIds) {

		heartbeat(showScheduleId, userId, sessionToken);
		validateSeatIds(scheduledSeatIds);
		List<Long> sortedSeatIds = scheduledSeatIds.stream().sorted().toList();

		List<ScheduledSeat> seats = scheduledSeatRepository.findAllById(sortedSeatIds);

		Preconditions.validate(sortedSeatIds.size() == seats.size(), ErrorCode.NOT_CORRECT_SEAT);

		for (ScheduledSeat seat : seats) {
			Preconditions.validate(
				seat.getShowScheduleId().equals(showScheduleId),
				ErrorCode.NOT_CORRECT_SEAT
			);

		}

		SeatHoldLockService.SeatHoldLock lock = seatHoldLockService.acquire(userId, showScheduleId);
		try {
			boolean released = ticketingRedisRepository.releaseHeldSeats(
				showScheduleId,
				sortedSeatIds,
				sessionToken
			);
			Preconditions.validate(released, ErrorCode.INVALID_HOLD_SEAT);
		} finally {
			releaseSeatHoldLock(lock);
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
		SeatHoldLockService.SeatHoldLock lock = seatHoldLockService.acquire(userId, showScheduleId);
		try {
			ticketingRedisRepository.releaseSessionHeldSeats(showScheduleId, sessionToken);
			ticketingRedisRepository.expireSessionToken(sessionToken);
			ticketingRedisRepository.exitTicketing(showScheduleId, sessionToken);
		} finally {
			releaseSeatHoldLock(lock);
		}
	}

	private void validateSeatIds(List<Long> scheduledSeatIds) {
		Preconditions.validate(
			scheduledSeatIds != null && !scheduledSeatIds.isEmpty(),
			ErrorCode.NOT_CORRECT_SEAT
		);
		Preconditions.validate(
			scheduledSeatIds.size() <= MAX_HOLD_SEAT_COUNT,
			ErrorCode.EXCEEDED_MAX_TICKET_COUNT
		);
		Preconditions.validate(
			scheduledSeatIds.stream().noneMatch(Objects::isNull),
			ErrorCode.NOT_CORRECT_SEAT
		);
		Preconditions.validate(
			new HashSet<>(scheduledSeatIds).size() == scheduledSeatIds.size(),
			ErrorCode.NOT_CORRECT_SEAT
		);
	}

	private void releaseSeatHoldLock(SeatHoldLockService.SeatHoldLock lock) {
		try {
			if (!seatHoldLockService.release(lock)) {
				log.warn("좌석 요청 락이 만료됐거나 소유권이 변경되었습니다. lockToken={}", lock.lockToken());
			}
		} catch (RuntimeException exception) {
			log.warn("좌석 요청 락 해제에 실패했습니다. lockToken={}", lock.lockToken(), exception);
		}
	}

	private void isCorrectSessionToken(Long showScheduleId, UUID userId, String sessionToken) {
		Preconditions.validate(sessionToken != null && !sessionToken.isBlank(), ErrorCode.INVALID_SESSION_TOKEN);


		SessionTicketValueDTO sessionValue = ticketingRedisRepository.getSessionTokenValue(sessionToken);

		Preconditions.validate(sessionValue != null, ErrorCode.INVALID_SESSION_TOKEN);
		Preconditions.validate(userId.equals(sessionValue.getUserId()), ErrorCode.SESSION_TOKEN_MISMATCH);
		Preconditions.validate(showScheduleId.equals(sessionValue.getShowScheduleId()), ErrorCode.SESSION_TOKEN_MISMATCH);
	}

}

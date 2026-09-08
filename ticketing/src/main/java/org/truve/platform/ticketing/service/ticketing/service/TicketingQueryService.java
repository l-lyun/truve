package org.truve.platform.ticketing.service.ticketing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketingQueryService {

	private final ShowScheduledRepository showScheduledRepository;
	private final ScheduledSeatRepository scheduledSeatRepository;

	@Transactional(readOnly = true)
	public TicketingResponse.Seats getSeats(Long showScheduleId) {
		showScheduledRepository.findById(showScheduleId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));
		return TicketingResponse.Seats.from(
			scheduledSeatRepository.findSeatSectionByScheduledSeatId(showScheduleId));
	}
}

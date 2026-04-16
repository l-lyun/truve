package org.truve.platform.ticketing.service.ticketing.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduledSeatStatusService {

	private final ScheduledSeatRepository scheduledSeatRepository;

	@Transactional
	public void holdSeats(TicketingEventCommand.HoldRequested event) {
		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllById(event.getScheduledSeatIds());
		Preconditions.validate(scheduledSeats.size() == event.getScheduledSeatIds().size(), ErrorCode.NOT_CORRECT_SEAT);
		scheduledSeats.forEach(ScheduledSeat::holdSeat);
	}

	@Transactional
	public void releaseSeats(TicketingEventCommand.HoldReleased event) {
		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllById(event.getScheduledSeatIds());
		Preconditions.validate(scheduledSeats.size() == event.getScheduledSeatIds().size(), ErrorCode.NOT_CORRECT_SEAT);
		scheduledSeats.forEach(ScheduledSeat::releaseSeat);
	}

	@Transactional
	public void purchaseSeats(TicketingEventCommand.SoldConfirmed event) {
		List<ScheduledSeat> scheduledSeats = scheduledSeatRepository.findAllById(event.getScheduledSeatIds());
		Preconditions.validate(scheduledSeats.size() == event.getScheduledSeatIds().size(), ErrorCode.NOT_CORRECT_SEAT);
		scheduledSeats.forEach(ScheduledSeat::purchaseSeat);
	}
}

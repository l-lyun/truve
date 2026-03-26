package org.truve.platform.ticketing.service.ticketing.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingInternalResponse;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

		@Service
		@RequiredArgsConstructor
		public class TicketingInternalService {

			private final ScheduledSeatRepository scheduledSeatRepository;
			private final ShowScheduledRepository showScheduledRepository;

			public TicketingInternalResponse.RemainingSeats getRemainingSeats(Long showScheduleId) {
				showScheduledRepository.findById(showScheduleId)
					.orElseThrow(() -> new CustomException(ErrorCode.INVALID_SHOW_SCHEDULE));

				List<TicketingInternalResponse.FlatRemainingSeatInfo> grades =
					scheduledSeatRepository.findGradeRemainingSeats(showScheduleId, SeatStatus.AVAILABLE);

				List<TicketingInternalResponse.GradeRemaining> remainingSeats = grades.stream()
					.map(TicketingInternalResponse.GradeRemaining::from)
					.toList();

				return TicketingInternalResponse.RemainingSeats.of(showScheduleId, remainingSeats);
	}

}

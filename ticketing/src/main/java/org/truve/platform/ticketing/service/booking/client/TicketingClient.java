package org.truve.platform.ticketing.service.booking.client;

import java.util.List;

import org.springframework.stereotype.Component;
import org.truve.platform.ticketing.service.booking.client.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.repository.SeatRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingClient {

	private final SeatRepository seatRepository;

	/*
	TODO: 통신 방식 정한 후 관련 메서드 삭제 및 수정 예정
	현재는 임시로 같은 서버의 Seat 테이블에서 정보를 조회함

	[관련 임시 메서드]
	SeatRepository.findAllWithSectionByIds
	ScheduleResponse.SeatInfo.from
	 */
	public List<TicketingResponse.SeatInfo> getSeatInfos(List<Long> seatIds) {
		List<Seat> seats = seatRepository.findAllWithSectionByIds(seatIds);

		return seats.stream().map(TicketingResponse.SeatInfo::from).toList();
	}
}

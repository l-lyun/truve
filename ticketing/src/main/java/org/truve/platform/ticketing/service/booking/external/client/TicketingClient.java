package org.truve.platform.ticketing.service.booking.external.client;

import static org.truve.platform.ticketing.service.booking.external.client.TicketingResponse.*;

import java.util.List;

import org.springframework.stereotype.Component;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingClient {

	private final ScheduledSeatRepository scheduledSeatRepository;

	/*
	TODO: 통신 방식 정한 후 관련 메서드 삭제 및 수정 예정
	현재는 임시로 같은 서버의 Seat 테이블에서 정보를 조회함

	[관련 임시 메서드]
	ScheduledSeatRepository.findFlatInfoById
	TicketingResponse
	 */
	public SeatInfo getSeatInfo(List<Long> seatIds) {
		List<FlatSeatInfo> flatList = scheduledSeatRepository.findFlatInfoById(seatIds);

		FlatSeatInfo first = flatList.getFirst();
		List<Seat> seats = flatList.stream().map(Seat::from).toList();

		return SeatInfo.from(first, seats);
	}
}

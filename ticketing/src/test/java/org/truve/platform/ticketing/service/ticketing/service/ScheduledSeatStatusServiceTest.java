package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ScheduledSeatStatusServiceTest {

	@Mock
	private ScheduledSeatRepository scheduledSeatRepository;

	@InjectMocks
	private ScheduledSeatStatusService scheduledSeatStatusService;

	@Test
	@DisplayName("RELEASE 요청이면 같은 예약이 소유한 HOLD 좌석만 AVAILABLE로 되돌린다.")
	void release요청_좌석해제_성공() {
		TicketingEventCommand.HoldReleased event = new TicketingEventCommand.HoldReleased(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L, 12L)
		);
		ScheduledSeat holdSeat = createScheduledSeat(10L, SeatStatus.HOLD);
		ScheduledSeat availableSeat = createScheduledSeat(11L, SeatStatus.AVAILABLE);
		ScheduledSeat soldSeat = createScheduledSeat(12L, SeatStatus.SOLD);
		given(scheduledSeatRepository.findAllByIdForUpdate(event.getScheduledSeatIds()))
			.willReturn(List.of(holdSeat, availableSeat, soldSeat));

		scheduledSeatStatusService.releaseSeats(event);

		assertThat(holdSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(availableSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(soldSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
	}

	@Test
	@DisplayName("늦게 도착한 RELEASE는 다른 예약이 소유한 좌석을 해제하지 않는다.")
	void release요청_다른예약소유_상태유지() {
		TicketingEventCommand.HoldReleased event = new TicketingEventCommand.HoldReleased(
			"R-OLD",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L)
		);
		ScheduledSeat seat = createScheduledSeat(10L, SeatStatus.HOLD, "R-NEW");
		given(scheduledSeatRepository.findAllByIdForUpdate(event.getScheduledSeatIds())).willReturn(List.of(seat));

		scheduledSeatStatusService.releaseSeats(event);

		assertThat(seat.getStatus()).isEqualTo(SeatStatus.HOLD);
		assertThat(seat.getReservationNumber()).isEqualTo("R-NEW");
	}

	@Test
	@DisplayName("SOLD_CONFIRMED 요청이면 HOLD 좌석을 SOLD 상태로 변경한다.")
	void sold요청_좌석구매_성공() {
		TicketingEventCommand.SoldConfirmed event = new TicketingEventCommand.SoldConfirmed(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		ScheduledSeat seat1 = createScheduledSeat(10L, SeatStatus.HOLD);
		ScheduledSeat seat2 = createScheduledSeat(11L, SeatStatus.HOLD);
		given(scheduledSeatRepository.findAllByIdForUpdate(event.getScheduledSeatIds())).willReturn(List.of(seat1, seat2));

		scheduledSeatStatusService.purchaseSeats(event);

		assertThat(seat1.getStatus()).isEqualTo(SeatStatus.SOLD);
		assertThat(seat2.getStatus()).isEqualTo(SeatStatus.SOLD);
	}

	@Test
	@DisplayName("같은 예약의 SOLD_CONFIRMED 이벤트가 재전달되면 상태를 유지한다.")
	void sold요청_중복이벤트_상태유지() {
		TicketingEventCommand.SoldConfirmed event = new TicketingEventCommand.SoldConfirmed(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L)
		);
		ScheduledSeat soldSeat = createScheduledSeat(10L, SeatStatus.SOLD);
		given(scheduledSeatRepository.findAllByIdForUpdate(event.getScheduledSeatIds())).willReturn(List.of(soldSeat));

		scheduledSeatStatusService.purchaseSeats(event);

		assertThat(soldSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
	}

	@Test
	@DisplayName("좌석 개수가 맞지 않으면 NOT_CORRECT_SEAT 예외가 발생한다.")
	void sold요청_좌석개수불일치() {
		TicketingEventCommand.SoldConfirmed event = new TicketingEventCommand.SoldConfirmed(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		given(scheduledSeatRepository.findAllByIdForUpdate(event.getScheduledSeatIds()))
			.willReturn(List.of(createScheduledSeat(10L, SeatStatus.HOLD)));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> scheduledSeatStatusService.purchaseSeats(event)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_SEAT);
	}

	private ScheduledSeat createScheduledSeat(Long scheduledSeatId, SeatStatus status) {
		return createScheduledSeat(scheduledSeatId, status, "R-001");
	}

	private ScheduledSeat createScheduledSeat(Long scheduledSeatId, SeatStatus status, String reservationNumber) {
		Seat seat = Seat.builder()
			.seatRow("A")
			.seatNumber(scheduledSeatId)
			.build();
		ReflectionTestUtils.setField(seat, "id", scheduledSeatId);

		ScheduledSeat scheduledSeat = ScheduledSeat.builder()
			.seat(seat)
			.showScheduleId(1L)
			.build();
		ReflectionTestUtils.setField(scheduledSeat, "id", scheduledSeatId);
		ReflectionTestUtils.setField(scheduledSeat, "status", status);
		if (status != SeatStatus.AVAILABLE) {
			ReflectionTestUtils.setField(scheduledSeat, "reservationNumber", reservationNumber);
		}
		return scheduledSeat;
	}
}

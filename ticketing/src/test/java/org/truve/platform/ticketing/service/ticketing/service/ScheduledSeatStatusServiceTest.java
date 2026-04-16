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
	@DisplayName("HOLD 요청이면 AVAILABLE 좌석을 HOLD 상태로 변경한다.")
	void hold요청_좌석점유_성공() {
		TicketingEventCommand.HoldRequested event = new TicketingEventCommand.HoldRequested(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		ScheduledSeat seat1 = createScheduledSeat(10L, SeatStatus.AVAILABLE);
		ScheduledSeat seat2 = createScheduledSeat(11L, SeatStatus.AVAILABLE);
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds())).willReturn(List.of(seat1, seat2));

		scheduledSeatStatusService.holdSeats(event);

		assertThat(seat1.getStatus()).isEqualTo(SeatStatus.HOLD);
		assertThat(seat2.getStatus()).isEqualTo(SeatStatus.HOLD);
	}

	@Test
	@DisplayName("이미 HOLD 또는 SOLD 인 좌석은 상태를 유지한다.")
	void hold요청_기존상태유지() {
		TicketingEventCommand.HoldRequested event = new TicketingEventCommand.HoldRequested(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		ScheduledSeat holdSeat = createScheduledSeat(10L, SeatStatus.HOLD);
		ScheduledSeat soldSeat = createScheduledSeat(11L, SeatStatus.SOLD);
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds())).willReturn(List.of(holdSeat, soldSeat));

		scheduledSeatStatusService.holdSeats(event);

		assertThat(holdSeat.getStatus()).isEqualTo(SeatStatus.HOLD);
		assertThat(soldSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
	}

	@Test
	@DisplayName("좌석 개수가 맞지 않으면 NOT_CORRECT_SEAT 예외가 발생한다.")
	void hold요청_좌석개수불일치() {
		TicketingEventCommand.HoldRequested event = new TicketingEventCommand.HoldRequested(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds()))
			.willReturn(List.of(createScheduledSeat(10L, SeatStatus.AVAILABLE)));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> scheduledSeatStatusService.holdSeats(event)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_SEAT);
	}

	@Test
	@DisplayName("RELEASE 요청이면 좌석 상태를 AVAILABLE로 되돌린다.")
	void release요청_좌석해제_성공() {
		TicketingEventCommand.HoldReleased event = new TicketingEventCommand.HoldReleased(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L, 12L)
		);
		ScheduledSeat holdSeat = createScheduledSeat(10L, SeatStatus.HOLD);
		ScheduledSeat availableSeat = createScheduledSeat(11L, SeatStatus.AVAILABLE);
		ScheduledSeat soldSeat = createScheduledSeat(12L, SeatStatus.SOLD);
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds()))
			.willReturn(List.of(holdSeat, availableSeat, soldSeat));

		scheduledSeatStatusService.releaseSeats(event);

		assertThat(holdSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(availableSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(soldSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
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
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds())).willReturn(List.of(seat1, seat2));

		scheduledSeatStatusService.purchaseSeats(event);

		assertThat(seat1.getStatus()).isEqualTo(SeatStatus.SOLD);
		assertThat(seat2.getStatus()).isEqualTo(SeatStatus.SOLD);
	}

	@Test
	@DisplayName("이미 SOLD인 좌석에 SOLD_CONFIRMED 요청이 오면 예외가 발생한다.")
	void sold요청_이미SOLD_예외발생() {
		TicketingEventCommand.SoldConfirmed event = new TicketingEventCommand.SoldConfirmed(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L)
		);
		ScheduledSeat soldSeat = createScheduledSeat(10L, SeatStatus.SOLD);
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds())).willReturn(List.of(soldSeat));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> scheduledSeatStatusService.purchaseSeats(event)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_SOLD_SEAT);
	}

	@Test
	@DisplayName("좌석 개수가 맞지 않으면 NOT_CORRECT_SEAT 예외가 발생한다.")
	void sold요청_좌석개수불일치() {
		TicketingEventCommand.SoldConfirmed event = new TicketingEventCommand.SoldConfirmed(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		given(scheduledSeatRepository.findAllById(event.getScheduledSeatIds()))
			.willReturn(List.of(createScheduledSeat(10L, SeatStatus.HOLD)));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> scheduledSeatStatusService.purchaseSeats(event)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_SEAT);
	}

	private ScheduledSeat createScheduledSeat(Long scheduledSeatId, SeatStatus status) {
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
		return scheduledSeat;
	}
}

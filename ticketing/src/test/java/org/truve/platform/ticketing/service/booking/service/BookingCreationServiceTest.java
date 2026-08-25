package org.truve.platform.ticketing.service.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BookingCreationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private ScheduledSeatRepository scheduledSeatRepository;
	@Mock
	private ShowScheduledRepository showScheduledRepository;

	@InjectMocks
	private BookingCreationService bookingCreationService;

	@Test
	void 예약_티켓_좌석HOLD를_함께_생성한다() {
		ScheduledSeat seat1 = createScheduledSeat(10L, SeatStatus.AVAILABLE);
		ScheduledSeat seat2 = createScheduledSeat(11L, SeatStatus.AVAILABLE);
		ShowScheduled show = createShowScheduled(100L);
		given(scheduledSeatRepository.findAllByIdForUpdate(List.of(10L, 11L)))
			.willReturn(List.of(seat1, seat2));
		given(showScheduledRepository.findById(100L)).willReturn(Optional.of(show));

		bookingCreationService.create(UUID.randomUUID(), 100L, List.of(11L, 10L), "R-001");

		ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).saveAndFlush(captor.capture());
		Reservation reservation = captor.getValue();
		assertAll(
			() -> assertThat(reservation.getBlockBooking()).isTrue(),
			() -> assertThat(reservation.getTickets()).hasSize(2),
			() -> assertThat(reservation.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING),
			() -> assertThat(seat1.getStatus()).isEqualTo(SeatStatus.HOLD),
			() -> assertThat(seat2.getStatus()).isEqualTo(SeatStatus.HOLD),
			() -> assertThat(seat1.getReservationNumber()).isEqualTo("R-001"),
			() -> assertThat(seat2.getReservationNumber()).isEqualTo("R-001")
		);
	}

	@Test
	void 하나라도_AVAILABLE이_아니면_아무것도_저장하지_않는다() {
		ScheduledSeat available = createScheduledSeat(10L, SeatStatus.AVAILABLE);
		ScheduledSeat hold = createScheduledSeat(11L, SeatStatus.HOLD);
		given(scheduledSeatRepository.findAllByIdForUpdate(List.of(10L, 11L)))
			.willReturn(List.of(available, hold));

		CustomException exception = assertThrows(
			CustomException.class,
			() -> bookingCreationService.create(UUID.randomUUID(), 100L, List.of(10L, 11L), "R-001")
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_HOLD_SEAT);
		assertThat(available.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		verify(reservationRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
	}

	private ScheduledSeat createScheduledSeat(Long id, SeatStatus status) {
		SeatSection section = SeatSection.builder()
			.venueId(1L)
			.name("A")
			.floor(1L)
			.gradeName("VIP")
			.price(10000L)
			.build();
		Seat seat = Seat.builder()
			.seatSection(section)
			.seatRow("A")
			.seatNumber(id)
			.build();
		ScheduledSeat scheduledSeat = ScheduledSeat.builder()
			.seat(seat)
			.showScheduleId(100L)
			.build();
		ReflectionTestUtils.setField(scheduledSeat, "id", id);
		ReflectionTestUtils.setField(scheduledSeat, "status", status);
		if (status != SeatStatus.AVAILABLE) {
			ReflectionTestUtils.setField(scheduledSeat, "reservationNumber", "OTHER");
		}
		return scheduledSeat;
	}

	private ShowScheduled createShowScheduled(Long id) {
		ShowScheduled show = ShowScheduled.builder()
			.showId(1L)
			.title("공연")
			.venueName("공연장")
			.startAt(LocalDateTime.now().plusDays(1))
			.posterImg("poster")
			.build();
		ReflectionTestUtils.setField(show, "id", id);
		return show;
	}
}

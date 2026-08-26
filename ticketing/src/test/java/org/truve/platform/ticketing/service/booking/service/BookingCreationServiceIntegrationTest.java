package org.truve.platform.ticketing.service.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatSectionRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

@DataJpaTest
@Import(BookingCreationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingCreationServiceIntegrationTest {
	@Autowired
	private BookingCreationService bookingCreationService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private SeatSectionRepository seatSectionRepository;
	@Autowired
	private SeatRepository seatRepository;
	@Autowired
	private ScheduledSeatRepository scheduledSeatRepository;
	@Autowired
	private ShowScheduledRepository showScheduledRepository;

	@Test
	void save만_사용해도_제약조건_예외가_호출자에게_전파되고_좌석은_롤백된다() {
		UUID userId = UUID.randomUUID();
		ShowScheduled show = showScheduledRepository.save(ShowScheduled.builder()
			.showId(1L)
			.title("공연")
			.venueName("공연장")
			.startAt(LocalDateTime.now().plusDays(1))
			.posterImg("poster")
			.build());
		SeatSection section = seatSectionRepository.save(SeatSection.builder()
			.venueId(1L)
			.name("A")
			.floor(1L)
			.gradeName("VIP")
			.price(100_000L)
			.build());
		Seat firstSeat = seatRepository.save(Seat.builder()
			.seatSection(section)
			.seatRow("A")
			.seatNumber(1L)
			.build());
		Seat secondSeat = seatRepository.save(Seat.builder()
			.seatSection(section)
			.seatRow("A")
			.seatNumber(2L)
			.build());
		ScheduledSeat firstScheduledSeat = scheduledSeatRepository.save(
			ScheduledSeat.builder().seat(firstSeat).showScheduleId(show.getId()).build()
		);
		ScheduledSeat secondScheduledSeat = scheduledSeatRepository.save(
			ScheduledSeat.builder().seat(secondSeat).showScheduleId(show.getId()).build()
		);

		bookingCreationService.create(
			userId, show.getId(), List.of(firstScheduledSeat.getId()), "R-001"
		);

		assertThatThrownBy(() -> bookingCreationService.create(
			userId, show.getId(), List.of(secondScheduledSeat.getId()), "R-002"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(reservationRepository.existsByNumber("R-001")).isTrue();
		assertThat(reservationRepository.existsByNumber("R-002")).isFalse();
		ScheduledSeat rolledBackSeat = scheduledSeatRepository.findById(secondScheduledSeat.getId()).orElseThrow();
		assertThat(rolledBackSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(rolledBackSeat.getReservationNumber()).isNull();
	}
}

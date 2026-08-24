package org.truve.platform.ticketing.service.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;

@DataJpaTest
class ReservationRepositoryTest {
	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Long SHOW_SCHEDULE_ID = 100L;

	@Autowired
	private ReservationRepository reservationRepository;

	@Test
	void 같은_사용자와_회차의_활성_예약은_하나만_저장된다() {
		reservationRepository.saveAndFlush(createReservation("R-001"));

		assertThat(reservationRepository.existsBlockingBooking(USER_ID, SHOW_SCHEDULE_ID)).isTrue();
		assertThatThrownBy(() -> reservationRepository.saveAndFlush(createReservation("R-002")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Reservation createReservation(String reservationNumber) {
		ShowInfo showInfo = ShowInfo.builder()
			.showId(10L)
			.showScheduleId(SHOW_SCHEDULE_ID)
			.title("공연")
			.venueName("공연장")
			.startAt(LocalDateTime.of(2026, 9, 1, 19, 0))
			.posterImg("poster.jpg")
			.build();

		return Reservation.create(USER_ID, reservationNumber, "VIP석 1인", showInfo);
	}
}

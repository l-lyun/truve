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
		reservationRepository.saveAndFlush(createReservation(USER_ID, SHOW_SCHEDULE_ID, "R-001"));

		assertThat(reservationRepository.existsBlockingBooking(USER_ID, SHOW_SCHEDULE_ID)).isTrue();
		assertThatThrownBy(() -> reservationRepository.saveAndFlush(
			createReservation(USER_ID, SHOW_SCHEDULE_ID, "R-002")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void holdId는_중복_저장할_수_없다() {
		LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 27, 12, 0);
		reservationRepository.saveAndFlush(createHoldPending(
			USER_ID, SHOW_SCHEDULE_ID, "R-HOLD-001", "H-001", expiresAt));

		assertThatThrownBy(() -> reservationRepository.saveAndFlush(createHoldPending(
			UUID.randomUUID(), SHOW_SCHEDULE_ID + 1, "R-HOLD-002", "H-001", expiresAt)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 기존_예약은_null_holdId로_여러_건_저장할_수_있다() {
		reservationRepository.saveAndFlush(createReservation(USER_ID, SHOW_SCHEDULE_ID, "R-001"));

		Reservation second = reservationRepository.saveAndFlush(createReservation(
			UUID.randomUUID(), SHOW_SCHEDULE_ID + 1, "R-002"));

		assertThat(second.getHoldId()).isNull();
	}

	private Reservation createReservation(UUID userId, Long showScheduleId, String reservationNumber) {
		return Reservation.create(userId, reservationNumber, "VIP석 1인", createShowInfo(showScheduleId));
	}

	private Reservation createHoldPending(
		UUID userId,
		Long showScheduleId,
		String reservationNumber,
		String holdId,
		LocalDateTime expiresAt
	) {
		return Reservation.createHoldPending(
			userId, reservationNumber, "VIP석 1인", createShowInfo(showScheduleId), holdId,
			"seat-fingerprint", expiresAt);
	}

	private ShowInfo createShowInfo(Long showScheduleId) {
		ShowInfo showInfo = ShowInfo.builder()
			.showId(10L)
			.showScheduleId(showScheduleId)
			.title("공연")
			.venueName("공연장")
			.startAt(LocalDateTime.of(2026, 9, 1, 19, 0))
			.posterImg("poster.jpg")
			.build();
		return showInfo;
	}
}

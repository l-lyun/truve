package org.truve.platform.ticketing.service.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;

import jakarta.persistence.EntityManager;

@DataJpaTest
class ScheduledSeatRepositoryTest {
	@Autowired
	private ScheduledSeatRepository scheduledSeatRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 좌석_상태를_변경하면_version이_증가한다() {
		ScheduledSeat seat = scheduledSeatRepository.saveAndFlush(ScheduledSeat.builder()
			.showScheduleId(100L)
			.build());
		Long initialVersion = seat.getVersion();

		seat.reserve("R-001", LocalDateTime.of(2026, 8, 27, 12, 0));
		scheduledSeatRepository.flush();
		entityManager.clear();

		ScheduledSeat updated = scheduledSeatRepository.findById(seat.getId()).orElseThrow();
		assertThat(initialVersion).isNotNull();
		assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
	}
}

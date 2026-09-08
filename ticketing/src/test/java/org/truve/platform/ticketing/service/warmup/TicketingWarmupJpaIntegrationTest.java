package org.truve.platform.ticketing.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;
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
import org.truve.platform.ticketing.service.ticketing.service.TicketingQueryService;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TicketingQueryService.class)
class TicketingWarmupJpaIntegrationTest {

	@Autowired TicketingQueryService queryService;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired ShowScheduledRepository shows;
	@Autowired SeatSectionRepository sections;
	@Autowired SeatRepository seats;
	@Autowired ScheduledSeatRepository scheduledSeats;
	@Autowired ReservationRepository reservations;
	@Autowired TicketingOutboxEventRepository outbox;

	@Test
	void 실제_JPA_조회와_웜업은_좌석_상태와_예약_outbox를_변경하지_않는다() {
		ShowScheduled show = shows.save(ShowScheduled.builder().showId(1L).title("웜업 공연")
			.venueName("공연장").startAt(LocalDateTime.of(2026, 9, 10, 19, 0)).posterImg("poster").build());
		SeatSection section = sections.save(SeatSection.builder().venueId(1L).name("VIP")
			.floor(1L).gradeName("VIP").price(100000L).build());
		Seat second = seats.save(Seat.builder().seatSection(section).seatRow("A").seatNumber(2L).build());
		Seat first = seats.save(Seat.builder().seatSection(section).seatRow("A").seatNumber(1L).build());
		ScheduledSeat available = scheduledSeats.save(ScheduledSeat.builder().seat(second).showScheduleId(show.getId()).build());
		ScheduledSeat held = ScheduledSeat.builder().seat(first).showScheduleId(show.getId()).build();
		held.reserve("existing-reservation", LocalDateTime.of(2026, 9, 9, 12, 0));
		held = scheduledSeats.save(held);
		long reservationCount = reservations.count();
		long outboxCount = outbox.count();
		Long originalVersion = held.getVersion();

		var runner = new TicketingWarmupRunner(
			new TicketingWarmupProperties(true, show.getId(), 3, Duration.ofSeconds(30), 5),
			queryService, JsonMapper.builder().build(), transactionManager);
		runner.run(new DefaultApplicationArguments());

		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.COMPLETED);
		var response = queryService.getSeats(show.getId());
		assertThat(response.getSections().getFirst().getRows().getFirst().getSeats())
			.extracting("col").containsExactly(1L, 2L);
		ScheduledSeat unchanged = scheduledSeats.findById(held.getId()).orElseThrow();
		assertThat(unchanged.getStatus()).isEqualTo(SeatStatus.HOLD);
		assertThat(unchanged.getReservationNumber()).isEqualTo("existing-reservation");
		assertThat(unchanged.getVersion()).isEqualTo(originalVersion);
		assertThat(scheduledSeats.findById(available.getId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(reservations.count()).isEqualTo(reservationCount);
		assertThat(outbox.count()).isEqualTo(outboxCount);
	}

	@Test
	void 존재하지_않는_회차는_기존_오류를_유지한다() {
		assertThatThrownBy(() -> queryService.getSeats(Long.MAX_VALUE))
			.isInstanceOfSatisfying(CustomException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOW_SCHEDULE));
	}
}

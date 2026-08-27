package org.truve.platform.ticketing.service.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;
import org.truve.platform.ticketing.service.booking.outbox.service.TicketingOutboxPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationCommand;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatSectionRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.support.JsonConverter;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	HoldReservationCreationService.class,
	TicketingOutboxPublisher.class,
	JsonConverter.class,
	HoldReservationCreationServiceIntegrationTest.TestConfig.class
})
class HoldReservationCreationServiceIntegrationTest {
	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

	@Autowired
	private HoldReservationCreationService service;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private ScheduledSeatRepository scheduledSeatRepository;
	@Autowired
	private SeatSectionRepository seatSectionRepository;
	@Autowired
	private SeatRepository seatRepository;
	@Autowired
	private ShowScheduledRepository showScheduledRepository;

	private ShowScheduled show;
	private ScheduledSeat firstSeat;
	private ScheduledSeat secondSeat;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
		reservationRepository.deleteAll();
		show = showScheduledRepository.save(ShowScheduled.builder()
			.showId(1L).title("공연").venueName("공연장")
			.startAt(LocalDateTime.of(2026, 9, 1, 19, 0)).posterImg("poster").build());
		SeatSection section = seatSectionRepository.save(SeatSection.builder()
			.venueId(1L).name("A").floor(1L).gradeName("VIP").price(100_000L).build());
		firstSeat = createScheduledSeat(section, 1L);
		secondSeat = createScheduledSeat(section, 2L);
	}

	@Test
	void Reservation과_HOLD_REQUESTED_Outbox를_함께_커밋한다() {
		LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 27, 16, 30);

		var result = service.create(command("H-001", firstSeat.getId(), expiresAt));

		assertThat(reservationRepository.findByHoldId("H-001")).isPresent();
		assertThat(result.status()).isEqualTo(ReservationStatus.HOLD_PENDING);
		assertThat(result.expiresAt()).isEqualTo(expiresAt);
		assertThat(outboxRepository.findAll()).singleElement().satisfies(event -> {
			assertThat(event.getEventType()).isEqualTo("HOLD_REQUESTED");
			assertThat(event.getMessageKey()).isEqualTo(result.reservationNumber());
			assertThat(event.getPayload()).contains("H-001", firstSeat.getId().toString());
		});
		assertThat(scheduledSeatRepository.findById(firstSeat.getId()).orElseThrow().getStatus())
			.isEqualTo(SeatStatus.AVAILABLE);
	}

	@Test
	void 활성_주문_제약으로_커밋이_실패하면_Reservation과_Outbox를_함께_롤백한다() {
		LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 27, 16, 30);
		service.create(command("H-001", firstSeat.getId(), expiresAt));

		assertThatThrownBy(() -> service.create(command("H-002", secondSeat.getId(), expiresAt)))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(reservationRepository.count()).isEqualTo(1);
		assertThat(outboxRepository.count()).isEqualTo(1);
		assertThat(reservationRepository.findByHoldId("H-002")).isEmpty();
	}

	@Test
	void 같은_holdId는_기존_주문을_반환하고_Outbox를_중복_저장하지_않는다() {
		LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 27, 16, 30);
		var first = service.create(command("H-001", firstSeat.getId(), expiresAt));

		var retried = service.create(command("H-001", firstSeat.getId(), expiresAt.plusMinutes(1)));

		assertThat(retried).isEqualTo(first);
		assertThat(reservationRepository.count()).isEqualTo(1);
		assertThat(outboxRepository.count()).isEqualTo(1);
	}

	private ScheduledSeat createScheduledSeat(SeatSection section, Long seatNumber) {
		Seat seat = seatRepository.save(Seat.builder()
			.seatSection(section).seatRow("A").seatNumber(seatNumber).build());
		return scheduledSeatRepository.save(
			ScheduledSeat.builder().seat(seat).showScheduleId(show.getId()).build());
	}

	private HoldReservationCommand command(String holdId, Long seatId, LocalDateTime expiresAt) {
		return new HoldReservationCommand(
			holdId, USER_ID, "session-token", show.getId(), List.of(seatId), expiresAt);
	}

	static class TestConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper().findAndRegisterModules();
		}
	}
}

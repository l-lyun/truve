package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;
import org.truve.platform.ticketing.service.booking.outbox.service.PaymentCreationOutboxPublisher;
import org.truve.platform.ticketing.service.booking.outbox.service.TicketingOutboxPublisher;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationCommand;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationResult;
import org.truve.platform.ticketing.service.booking.util.NumberGenerator;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatSectionRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedFailureService.FailureRecordResult;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedTransactionService.ApplyResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.support.JsonConverter;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	HoldReservationCreationService.class,
	HoldRequestedTransactionService.class,
	HoldRequestedFailureService.class,
	TicketingOutboxPublisher.class,
	PaymentCreationOutboxPublisher.class,
	JsonConverter.class,
	HoldRequestedTransactionServiceIntegrationTest.TestConfig.class
})
class HoldRequestedTransactionServiceIntegrationTest {
	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 0);
	private static final LocalDateTime EXPIRES_AT = NOW.plusMinutes(10);

	@Autowired
	private HoldReservationCreationService creationService;
	@Autowired
	private HoldRequestedTransactionService transactionService;
	@Autowired
	private HoldRequestedFailureService failureService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private ScheduledSeatRepository scheduledSeatRepository;
	@Autowired
	private SeatRepository seatRepository;
	@Autowired
	private SeatSectionRepository seatSectionRepository;
	@Autowired
	private ShowScheduledRepository showScheduledRepository;

	private ShowScheduled show;
	private ScheduledSeat firstSeat;
	private ScheduledSeat secondSeat;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
		reservationRepository.deleteAll();
		scheduledSeatRepository.deleteAll();
		seatRepository.deleteAll();
		seatSectionRepository.deleteAll();
		showScheduledRepository.deleteAll();

		show = showScheduledRepository.save(ShowScheduled.builder()
			.showId(1L).title("공연").venueName("공연장")
			.startAt(LocalDateTime.of(2026, 9, 1, 19, 0)).posterImg("poster").build());
		SeatSection section = seatSectionRepository.save(SeatSection.builder()
			.venueId(1L).name("A").floor(1L).gradeName("VIP").price(100_000L).build());
		firstSeat = createScheduledSeat(section, 1L);
		secondSeat = createScheduledSeat(section, 2L);
	}

	@Test
	void 좌석_Ticket_Reservation_Payment_Outbox를_한_트랜잭션으로_확정한다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId(), secondSeat.getId()));

		ApplyResult result = transactionService.apply(event);

		assertThat(result).isEqualTo(ApplyResult.APPLIED);
		var reservation = reservationRepository.findByHoldIdWithTickets(event.getHoldId()).orElseThrow();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_READY);
		assertThat(reservation.getTickets()).hasSize(2).allSatisfy(ticket -> {
			assertThat(ticket.getNumber()).isNotBlank();
			assertThat(ticket.getGrade()).isEqualTo("VIP");
			assertThat(ticket.getPriceSnapshot()).isEqualTo(100_000L);
			assertThat(ticket.getSeatDetail()).contains("1층", "A구역", "A열");
		});
		assertThat(reservation.getTotalAmount()).isEqualTo(204_000L);
		assertThat(reservation.getServiceFee()).isEqualTo(4_000L);
		assertThat(scheduledSeatRepository.findAllById(event.getScheduledSeatIds()))
			.allSatisfy(seat -> {
				assertThat(seat.getStatus()).isEqualTo(SeatStatus.HOLD);
				assertThat(seat.getReservationNumber()).isEqualTo(event.getReservationNumber());
				assertThat(seat.getReservedAt()).isEqualTo(NOW);
			});
		assertThat(outboxRepository.findAll()).anySatisfy(outbox -> {
			assertThat(outbox.getTopic()).isEqualTo("booking.payment");
			assertThat(outbox.getMessageKey()).isEqualTo(event.getReservationNumber());
			assertThat(outbox.getEventType()).isEqualTo("CREATE");
			assertThat(outbox.getPayload()).contains(event.getReservationNumber(), "204000");
		});
	}

	@Test
	void 같은_이벤트를_재처리해도_Ticket과_Payment_Outbox를_중복_생성하지_않는다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId(), secondSeat.getId()));
		transactionService.apply(event);
		var first = reservationRepository.findByHoldIdWithTickets(event.getHoldId()).orElseThrow();
		List<Long> ticketIds = first.getTickets().stream().map(ticket -> ticket.getId()).sorted().toList();
		Long reservationVersion = first.getVersion();
		long outboxCount = outboxRepository.count();

		ApplyResult retried = transactionService.apply(event);

		var reloaded = reservationRepository.findByHoldIdWithTickets(event.getHoldId()).orElseThrow();
		assertThat(retried).isEqualTo(ApplyResult.ALREADY_APPLIED);
		assertThat(reloaded.getTickets().stream().map(ticket -> ticket.getId()).sorted().toList())
			.isEqualTo(ticketIds);
		assertThat(reloaded.getVersion()).isEqualTo(reservationVersion);
		assertThat(outboxRepository.count()).isEqualTo(outboxCount);
	}

	@Test
	void 결제_이후에_늦게_도착한_중복이벤트는_현재_좌석상태와_무관하게_멱등_성공한다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId()));
		transactionService.apply(event);
		var reservation = reservationRepository.findByHoldIdWithTickets(event.getHoldId()).orElseThrow();
		ReflectionTestUtils.setField(reservation, "status", ReservationStatus.CANCELED);
		reservationRepository.saveAndFlush(reservation);
		firstSeat = scheduledSeatRepository.findById(firstSeat.getId()).orElseThrow();
		firstSeat.releaseSeat(event.getReservationNumber());
		scheduledSeatRepository.saveAndFlush(firstSeat);
		long outboxCount = outboxRepository.count();

		ApplyResult result = transactionService.apply(event);

		assertThat(result).isEqualTo(ApplyResult.ALREADY_APPLIED);
		assertThat(outboxRepository.count()).isEqualTo(outboxCount);
	}

	@Test
	void 결제_이후_주문은_늦은_실패처리로_되돌리지_않는다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId()));
		transactionService.apply(event);
		var reservation = reservationRepository.findByHoldIdWithTickets(event.getHoldId()).orElseThrow();
		ReflectionTestUtils.setField(reservation, "status", ReservationStatus.CONFIRMED);
		reservationRepository.saveAndFlush(reservation);

		FailureRecordResult result = failureService.record(
			event, HoldRequestedApplyException.FailureReason.SEAT_CONFLICT);

		assertThat(result).isEqualTo(FailureRecordResult.COMPLETED_IGNORED);
		assertThat(reservationRepository.findByHoldId(event.getHoldId()).orElseThrow().getStatus())
			.isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	void 좌석_하나가_충돌하면_나머지_좌석_Ticket_Reservation_Outbox도_전부_롤백한다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId(), secondSeat.getId()));
		secondSeat.reserve("R-OTHER", NOW.minusMinutes(1));
		scheduledSeatRepository.saveAndFlush(secondSeat);
		long outboxCount = outboxRepository.count();

		assertThatThrownBy(() -> transactionService.apply(event))
			.isInstanceOf(HoldRequestedApplyException.class);

		assertThat(scheduledSeatRepository.findById(firstSeat.getId()).orElseThrow().getStatus())
			.isEqualTo(SeatStatus.AVAILABLE);
		assertThat(scheduledSeatRepository.findById(secondSeat.getId()).orElseThrow().getReservationNumber())
			.isEqualTo("R-OTHER");
		var reservation = reservationRepository.findByHoldIdWithTickets(event.getHoldId()).orElseThrow();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HOLD_PENDING);
		assertThat(reservation.getTickets()).isEmpty();
		assertThat(outboxRepository.count()).isEqualTo(outboxCount);
	}

	@Test
	void 실패_트랜잭션은_EXPIRED와_활성주문_해제를_별도로_커밋한다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId()));

		FailureRecordResult result = failureService.record(
			event, HoldRequestedApplyException.FailureReason.EXPIRED);

		var reservation = reservationRepository.findByHoldId(event.getHoldId()).orElseThrow();
		assertThat(result).isEqualTo(FailureRecordResult.RECORDED);
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(reservation.getBlockBooking()).isNull();
		assertThat(reservationRepository.existsBlockingBooking(USER_ID, show.getId())).isFalse();
	}

	@Test
	void 다른_좌석목록의_오염된_이벤트는_정상_주문을_실패시키지_않는다() {
		TicketingEventCommand.HoldRequested original = createEvent(List.of(firstSeat.getId()));
		TicketingEventCommand.HoldRequested corrupted = TicketingEventCommand.HoldRequested.of(
			original.getHoldId(), original.getReservationNumber(), USER_ID, "session-token",
			show.getId(), List.of(secondSeat.getId()), EXPIRES_AT);

		assertThatThrownBy(() -> failureService.record(
			corrupted, HoldRequestedApplyException.FailureReason.SEAT_CONFLICT))
			.isInstanceOf(CustomException.class);

		assertThat(reservationRepository.findByHoldId(original.getHoldId()).orElseThrow().getStatus())
			.isEqualTo(ReservationStatus.HOLD_PENDING);
	}

	@Test
	@Transactional
	void Consumer가_끝내_처리하지_못한_만료_HOLD_PENDING을_정리한다() {
		TicketingEventCommand.HoldRequested event = createEvent(List.of(firstSeat.getId()));

		int expired = reservationRepository.expirePendingHolds(
			EXPIRES_AT, ReservationStatus.HOLD_PENDING, ReservationStatus.EXPIRED);

		var reservation = reservationRepository.findByHoldId(event.getHoldId()).orElseThrow();
		assertThat(expired).isEqualTo(1);
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(reservation.getBlockBooking()).isNull();
	}

	private TicketingEventCommand.HoldRequested createEvent(List<Long> seatIds) {
		String holdId = "H-" + seatIds.stream().map(String::valueOf).reduce((a, b) -> a + "-" + b).orElseThrow();
		String fingerprint = NumberGenerator.generateHoldRequestFingerprint(seatIds);
		HoldReservationResult created = creationService.create(new HoldReservationCommand(
			holdId, fingerprint, USER_ID, "session-token", show.getId(), seatIds, EXPIRES_AT));
		return TicketingEventCommand.HoldRequested.of(
			holdId, created.reservationNumber(), USER_ID, "session-token", show.getId(), seatIds, EXPIRES_AT);
	}

	private ScheduledSeat createScheduledSeat(SeatSection section, Long seatNumber) {
		Seat seat = seatRepository.save(Seat.builder()
			.seatSection(section).seatRow("A").seatNumber(seatNumber).build());
		return scheduledSeatRepository.save(
			ScheduledSeat.builder().seat(seat).showScheduleId(show.getId()).build());
	}

	static class TestConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper().findAndRegisterModules();
		}

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-08-27T08:00:00Z"), ZoneId.of("Asia/Seoul"));
		}
	}
}

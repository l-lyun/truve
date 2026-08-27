package org.truve.platform.ticketing.service.ticketing.domain.entity;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.SeatSectionRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduledSeatOptimisticLockMySqlIntegrationTest {
	private static final String MYSQL_DATABASE = "ticketing_test";
	private static final String MYSQL_USERNAME = "test";
	private static final String MYSQL_PASSWORD = "test";

	@Container
	private static final GenericContainer<?> MYSQL = new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
		.withEnv("MYSQL_DATABASE", MYSQL_DATABASE)
		.withEnv("MYSQL_USER", MYSQL_USERNAME)
		.withEnv("MYSQL_PASSWORD", MYSQL_PASSWORD)
		.withEnv("MYSQL_ROOT_PASSWORD", "root")
		.withExposedPorts(3306);

	@DynamicPropertySource
	static void mysqlProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
			+ MYSQL.getMappedPort(3306) + "/" + MYSQL_DATABASE
			+ "?useSSL=false&allowPublicKeyRetrieval=true");
		registry.add("spring.datasource.username", () -> MYSQL_USERNAME);
		registry.add("spring.datasource.password", () -> MYSQL_PASSWORD);
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
	}

	@Autowired
	private ScheduledSeatRepository scheduledSeatRepository;
	@Autowired
	private SeatRepository seatRepository;
	@Autowired
	private SeatSectionRepository seatSectionRepository;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private Fixture fixture;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
		reservationRepository.deleteAll();
		scheduledSeatRepository.deleteAll();
		seatRepository.deleteAll();
		seatSectionRepository.deleteAll();
		fixture = saveFixture();
	}

	@Test
	void 다중좌석_트랜잭션에서_한좌석의_version이_충돌하면_나머지_변경도_모두_롤백한다() throws Exception {
		CountDownLatch holdChangesPrepared = new CountDownLatch(1);
		CountDownLatch competingCommitCompleted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Attempt holdAttempt;

		try {
			Future<Attempt> holdFuture = executor.submit(
				() -> attemptHoldAllSeats(holdChangesPrepared, competingCommitCompleted));
			Future<?> competingFuture = executor.submit(
				() -> reserveSecondSeatForAnotherReservation(holdChangesPrepared, competingCommitCompleted));

			holdAttempt = holdFuture.get(15, SECONDS);
			competingFuture.get(15, SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(holdAttempt.failure()).isNotNull();
		assertThat(isOptimisticLockFailure(holdAttempt.failure())).isTrue();

		List<ScheduledSeat> seats = scheduledSeatRepository.findAllById(
			List.of(fixture.firstSeatId(), fixture.secondSeatId()));
		ScheduledSeat first = seats.stream()
			.filter(seat -> seat.getId().equals(fixture.firstSeatId()))
			.findFirst()
			.orElseThrow();
		ScheduledSeat second = seats.stream()
			.filter(seat -> seat.getId().equals(fixture.secondSeatId()))
			.findFirst()
			.orElseThrow();

		assertThat(first.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(first.getReservationNumber()).isNull();
		assertThat(first.getVersion()).isZero();
		assertThat(second.getStatus()).isEqualTo(SeatStatus.HOLD);
		assertThat(second.getReservationNumber()).isEqualTo("R-OTHER");
		assertThat(second.getVersion()).isEqualTo(1L);

		Reservation reservation = reservationRepository.findByHoldIdWithTickets("H-001").orElseThrow();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HOLD_PENDING);
		assertThat(reservation.getVersion()).isZero();
		assertThat(reservation.getTickets()).isEmpty();
		assertThat(outboxRepository.findAll())
			.singleElement()
			.satisfies(outbox -> assertThat(outbox.getEventType()).isEqualTo("HOLD_REQUESTED"));
	}

	private Attempt attemptHoldAllSeats(
		CountDownLatch holdChangesPrepared,
		CountDownLatch competingCommitCompleted
	) {
		try {
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				Reservation reservation = reservationRepository.findByHoldIdWithTickets("H-001").orElseThrow();
				List<ScheduledSeat> seats = scheduledSeatRepository.findAllByIdWithSeat(
					List.of(fixture.firstSeatId(), fixture.secondSeatId()));

				reservation.addTickets(List.of(
					Ticket.create(reservation, "T-001", "VIP", 100_000L, "1층 A구역 A열 1번",
						fixture.firstSeatId()),
					Ticket.create(reservation, "T-002", "VIP", 100_000L, "1층 A구역 A열 2번",
						fixture.secondSeatId())
				));
				seats.forEach(seat -> seat.reserve("R-001", LocalDateTime.now()));
				reservation.completeHold();
				outboxRepository.save(TicketingOutboxEvent.create(
					"booking.payment", "R-001", "{}", "CREATE"));

				holdChangesPrepared.countDown();
				await(competingCommitCompleted, 10);
			});
			return new Attempt(null);
		} catch (RuntimeException exception) {
			return new Attempt(exception);
		}
	}

	private void reserveSecondSeatForAnotherReservation(
		CountDownLatch holdChangesPrepared,
		CountDownLatch competingCommitCompleted
	) {
		await(holdChangesPrepared, 10);
		try {
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				ScheduledSeat second = scheduledSeatRepository.findById(fixture.secondSeatId()).orElseThrow();
				second.reserve("R-OTHER", LocalDateTime.now());
				scheduledSeatRepository.flush();
			});
		} finally {
			competingCommitCompleted.countDown();
		}
	}

	private Fixture saveFixture() {
		return new TransactionTemplate(transactionManager).execute(status -> {
			SeatSection section = seatSectionRepository.save(SeatSection.builder()
				.venueId(1L).name("A").floor(1L).gradeName("VIP").price(100_000L).build());
			Seat firstSeat = seatRepository.save(Seat.builder()
				.seatSection(section).seatRow("A").seatNumber(1L).build());
			Seat secondSeat = seatRepository.save(Seat.builder()
				.seatSection(section).seatRow("A").seatNumber(2L).build());
			ScheduledSeat first = scheduledSeatRepository.save(ScheduledSeat.builder()
				.seat(firstSeat).showScheduleId(100L).build());
			ScheduledSeat second = scheduledSeatRepository.save(ScheduledSeat.builder()
				.seat(secondSeat).showScheduleId(100L).build());

			Reservation reservation = Reservation.createHoldPending(
				UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
				"R-001",
				"VIP석 2인",
				ShowInfo.builder()
					.showId(1L)
					.showScheduleId(100L)
					.title("공연")
					.venueName("공연장")
					.startAt(LocalDateTime.now().plusDays(30))
					.posterImg("poster.jpg")
					.build(),
				"H-001",
				"fingerprint",
				LocalDateTime.now().plusMinutes(10)
			);
			reservationRepository.saveAndFlush(reservation);
			outboxRepository.saveAndFlush(TicketingOutboxEvent.create(
				"booking.ticketing", "R-001", "{}", "HOLD_REQUESTED"));
			return new Fixture(first.getId(), second.getId());
		});
	}

	private void await(CountDownLatch latch, long timeoutSeconds) {
		try {
			if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
				throw new IllegalStateException("동시성 테스트 대기 시간이 초과됐습니다.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
		}
	}

	private boolean isOptimisticLockFailure(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof jakarta.persistence.OptimisticLockException
				|| current instanceof org.springframework.orm.ObjectOptimisticLockingFailureException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private record Fixture(Long firstSeatId, Long secondSeatId) {
	}

	private record Attempt(RuntimeException failure) {
	}
}

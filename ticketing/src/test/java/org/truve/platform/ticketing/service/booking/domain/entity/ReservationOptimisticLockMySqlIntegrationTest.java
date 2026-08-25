package org.truve.platform.ticketing.service.booking.domain.entity;

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
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation.PaymentTransitionResult;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationOptimisticLockMySqlIntegrationTest {
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
	private ReservationRepository reservationRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void cleanDatabase() {
		reservationRepository.deleteAll();
	}

	@Test
	void 동일_예약을_동시에_결제완료하면_하나만_커밋되고_패자는_낙관적락으로_실패한다() throws Exception {
		savePendingPaymentReservation();
		CountDownLatch reservationsLoaded = new CountDownLatch(2);
		CountDownLatch updateStart = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Attempt first;
		Attempt second;

		try {
			Future<Attempt> firstFuture = executor.submit(() -> attemptConfirm(reservationsLoaded, updateStart));
			Future<Attempt> secondFuture = executor.submit(() -> attemptConfirm(reservationsLoaded, updateStart));
			assertThat(reservationsLoaded.await(5, SECONDS)).isTrue();
			updateStart.countDown();
			first = firstFuture.get(10, SECONDS);
			second = secondFuture.get(10, SECONDS);
		} finally {
			executor.shutdownNow();
		}

		List<Attempt> attempts = List.of(first, second);
		assertThat(attempts).filteredOn(attempt -> attempt.failure() == null).hasSize(1);
		assertThat(attempts).filteredOn(attempt -> attempt.failure() != null).hasSize(1);
		Attempt failed = attempts.stream().filter(attempt -> attempt.failure() != null).findFirst().orElseThrow();
		assertThat(isOptimisticLockFailure(failed.failure())).isTrue();

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED);
		assertThat(saved.getVersion()).isEqualTo(1L);
	}

	@Test
	void 커밋된_결제완료를_새_트랜잭션에서_다시_처리하면_상태와_버전을_변경하지_않는다() {
		savePendingPaymentReservation();

		PaymentTransitionResult first = new TransactionTemplate(transactionManager).execute(status -> {
			Reservation reservation = reservationRepository.findByNumber("R-001");
			return reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
		});
		PaymentTransitionResult duplicate = new TransactionTemplate(transactionManager).execute(status -> {
			Reservation reservation = reservationRepository.findByNumber("R-001");
			return reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
		});

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(first).isEqualTo(PaymentTransitionResult.CONFIRMED);
		assertThat(duplicate).isEqualTo(PaymentTransitionResult.ALREADY_APPLIED);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED);
		assertThat(saved.getVersion()).isEqualTo(1L);
	}

	private Attempt attemptConfirm(CountDownLatch reservationsLoaded, CountDownLatch updateStart) {
		try {
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				Reservation reservation = reservationRepository.findByNumber("R-001");
				reservationsLoaded.countDown();
				await(reservationsLoaded, 5);
				await(updateStart, 5);
				reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
			});
			return new Attempt(null);
		} catch (RuntimeException exception) {
			return new Attempt(exception);
		}
	}

	private void savePendingPaymentReservation() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			Reservation reservation = Reservation.create(
				UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
				"R-001",
				"VIP석 1인",
				ShowInfo.builder()
					.showId(1L)
					.showScheduleId(100L)
					.title("공연")
					.venueName("공연장")
					.startAt(LocalDateTime.now().plusDays(30))
					.posterImg("poster.jpg")
					.build()
			);
			reservation.addTickets(List.of(Ticket.create(
				reservation, "T-001", "VIP", 10000L, "1층 A구역 1열 1번", 10L
			)));
			reservation.readyForPayment(null);
			reservationRepository.saveAndFlush(reservation);
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

	private record Attempt(RuntimeException failure) {
	}
}

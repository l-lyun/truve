package org.truve.platform.ticketing.service.booking.inbox.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.inbox.repository.PaymentEventInboxRepository;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.service.BookingService;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentEventInboxHandler.class, PaymentEventProcessor.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentEventInboxMySqlIntegrationTest {
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
	private PaymentEventInboxHandler handler;
	@Autowired
	private PaymentEventInboxRepository inboxRepository;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoBean
	private BookingService bookingService;

	@BeforeEach
	void cleanDatabase() {
		inboxRepository.deleteAll();
		reservationRepository.deleteAll();
	}

	@Test
	void 두_인스턴스가_동일_eventId를_처리해도_Inbox와_비즈니스_처리는_한_건만_커밋된다() throws Exception {
		UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingEventCommand.Confirmed event = confirmedEvent();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<?> first = executor.submit(() -> handleAfterSignal(eventId, event, ready, start));
			Future<?> second = executor.submit(() -> handleAfterSignal(eventId, event, ready, start));
			assertThat(ready.await(5, SECONDS)).isTrue();
			start.countDown();
			first.get(10, SECONDS);
			second.get(10, SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(inboxRepository.count()).isEqualTo(1L);
		verify(bookingService, times(1)).confirm(event);
	}

	@Test
	void 서로_다른_eventId의_동시_결제는_낙관적락_패자의_Inbox까지_롤백하고_재처리된다() throws Exception {
		savePendingPaymentReservation();
		UUID firstEventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
		UUID secondEventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
		BookingEventCommand.Confirmed event = confirmedEvent();
		CountDownLatch reservationsLoaded = new CountDownLatch(2);
		CountDownLatch updateStart = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();

		doAnswer(invocation -> {
			Reservation reservation = reservationRepository.findByNumber("R-001");
			if (calls.incrementAndGet() <= 2) {
				reservationsLoaded.countDown();
				await(reservationsLoaded, 5);
				await(updateStart, 5);
			}
			reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
			return null;
		}).when(bookingService).confirm(any(BookingEventCommand.Confirmed.class));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		Attempt first;
		Attempt second;
		try {
			Future<Attempt> firstFuture = executor.submit(() -> attempt(firstEventId, event));
			Future<Attempt> secondFuture = executor.submit(() -> attempt(secondEventId, event));
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
		assertThat(inboxRepository.count()).isEqualTo(1L);

		handler.handle(failed.eventId(), "CONFIRMED", event);

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(inboxRepository.count()).isEqualTo(2L);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED);
		assertThat(saved.getVersion()).isEqualTo(1L);
	}

	private void handleAfterSignal(
		UUID eventId,
		BookingEventCommand.Confirmed event,
		CountDownLatch ready,
		CountDownLatch start
	) {
		ready.countDown();
		await(start, 5);
		handler.handle(eventId, "CONFIRMED", event);
	}

	private Attempt attempt(UUID eventId, BookingEventCommand.Confirmed event) {
		try {
			handler.handle(eventId, "CONFIRMED", event);
			return new Attempt(eventId, null);
		} catch (RuntimeException exception) {
			return new Attempt(eventId, exception);
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

	private BookingEventCommand.Confirmed confirmedEvent() {
		return new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);
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

	private record Attempt(UUID eventId, RuntimeException failure) {
	}
}

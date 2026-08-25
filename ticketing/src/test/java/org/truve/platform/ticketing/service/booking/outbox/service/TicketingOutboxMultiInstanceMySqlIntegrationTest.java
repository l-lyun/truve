package org.truve.platform.ticketing.service.booking.outbox.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketingOutboxMultiInstanceMySqlIntegrationTest {
	private static final String MYSQL_DATABASE = "ticketing_outbox_test";
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
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void cleanDatabase() {
		outboxRepository.deleteAll();
	}

	@Test
	void 두_Relay가_동시에_claim해도_서로_다른_Outbox를_가져간다() throws Exception {
		for (int index = 1; index <= 20; index++) {
			outboxRepository.save(event("R-" + index, "SOLD_CONFIRMED"));
		}
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch bothSelected = new CountDownLatch(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		ClaimAttempt first;
		ClaimAttempt second;

		try {
			Future<ClaimAttempt> firstFuture = executor.submit(() -> claim(10, start, bothSelected));
			Future<ClaimAttempt> secondFuture = executor.submit(() -> claim(10, start, bothSelected));
			start.countDown();
			first = firstFuture.get(10, SECONDS);
			second = secondFuture.get(10, SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(first.failure()).isNull();
		assertThat(second.failure()).isNull();
		assertThat(first.ids()).isNotEmpty();
		assertThat(second.ids()).isNotEmpty();
		assertThat(first.ids()).doesNotContainAnyElementsOf(second.ids());
		Set<Long> claimedIds = new HashSet<>(first.ids());
		claimedIds.addAll(second.ids());
		ClaimAttempt remaining = claim(20, new CountDownLatch(0), new CountDownLatch(0));
		assertThat(remaining.failure()).isNull();
		assertThat(remaining.ids()).allMatch(id -> !claimedIds.contains(id));
		claimedIds.addAll(remaining.ids());
		assertThat(claimedIds).hasSize(20);
		Set<UUID> expectedTokens = new HashSet<>(List.of(first.token(), second.token()));
		if (!remaining.ids().isEmpty()) {
			expectedTokens.add(remaining.token());
		}
		List<TicketingOutboxEvent> saved = outboxRepository.findAll();
		assertThat(saved).allMatch(event -> event.getStatus() == OutboxStatus.PROCESSING);
		assertThat(saved).extracting(TicketingOutboxEvent::getClaimToken)
			.contains(first.token(), second.token())
			.allMatch(expectedTokens::contains);
	}

	@Test
	void 같은_예약의_후속_판매취소는_다른_Relay가_선행_SOLD를_claim해도_추월하지_않는다() throws Exception {
		TicketingOutboxEvent sold = outboxRepository.save(event("R-001", "SOLD_CONFIRMED"));
		TicketingOutboxEvent canceled = outboxRepository.save(event("R-001", "SALE_CANCELED"));
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch bothSelected = new CountDownLatch(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		ClaimAttempt first;
		ClaimAttempt second;

		try {
			Future<ClaimAttempt> firstFuture = executor.submit(() -> claim(1, start, bothSelected));
			Future<ClaimAttempt> secondFuture = executor.submit(() -> claim(1, start, bothSelected));
			start.countDown();
			first = firstFuture.get(10, SECONDS);
			second = secondFuture.get(10, SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(first.failure()).isNull();
		assertThat(second.failure()).isNull();
		List<Long> allClaimed = java.util.stream.Stream.concat(first.ids().stream(), second.ids().stream()).toList();
		assertThat(allClaimed).containsExactly(sold.getId());
		TicketingOutboxEvent blocked = outboxRepository.findById(canceled.getId()).orElseThrow();
		assertThat(blocked.getStatus()).isEqualTo(OutboxStatus.PENDING);
	}

	private ClaimAttempt claim(int batchSize, CountDownLatch start, CountDownLatch bothSelected) {
		UUID token = UUID.randomUUID();
		try {
			TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
			transactionTemplate.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
			List<Long> ids = transactionTemplate.execute(status -> {
				await(start, 5);
				List<TicketingOutboxEvent> selected = outboxRepository.findClaimableHeadsForUpdate(batchSize);
				selected.forEach(event -> event.claim(token, LocalDateTime.now()));
				outboxRepository.flush();
				bothSelected.countDown();
				await(bothSelected, 5);
				return selected.stream().map(TicketingOutboxEvent::getId).toList();
			});
			return new ClaimAttempt(token, ids, null);
		} catch (RuntimeException exception) {
			bothSelected.countDown();
			return new ClaimAttempt(token, List.of(), exception);
		}
	}

	private TicketingOutboxEvent event(String reservationNumber, String eventType) {
		return TicketingOutboxEvent.create("booking.ticketing", reservationNumber, "{}", eventType);
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

	private record ClaimAttempt(UUID token, List<Long> ids, RuntimeException failure) {
	}
}

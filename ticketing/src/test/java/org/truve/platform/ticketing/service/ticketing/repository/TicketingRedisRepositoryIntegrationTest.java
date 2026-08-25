package org.truve.platform.ticketing.service.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.truve.platform.ticketing.service.global.support.RedisSupport;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository.SeatHoldResult;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
class TicketingRedisRepositoryIntegrationTest {
	private static final Long SHOW_SCHEDULE_ID = 100L;
	private static final String SESSION_A = "session-a";
	private static final String SESSION_B = "session-b";

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
		.withExposedPorts(6379);

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static TicketingRedisRepository repository;

	@BeforeAll
	static void setUpRedis() {
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
			REDIS.getHost(),
			REDIS.getMappedPort(6379)
		);
		connectionFactory = new LettuceConnectionFactory(configuration);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		repository = new TicketingRedisRepository(new RedisSupport(redisTemplate, new ObjectMapper()));
	}

	@AfterAll
	static void tearDownRedis() {
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@BeforeEach
	void flushRedis() {
		try (RedisConnection connection = connectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

	@Test
	void 여러_좌석_중_하나가_다른_세션_소유면_아무_좌석도_추가하지_않는다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(10L, 11L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID,
			List.of(11L, 12L),
			SESSION_B,
			4
		);

		assertThat(result).isEqualTo(SeatHoldResult.CONFLICT);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 10L)).isEqualTo(SESSION_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 11L)).isEqualTo(SESSION_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 12L)).isNull();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_B))).isEmpty();
	}

	@Test
	void 여러_요청을_합산해_세션당_네_좌석을_초과할_수_없다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(1L, 2L, 3L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(4L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);

		SeatHoldResult result = repository.holdSeats(SHOW_SCHEDULE_ID, List.of(5L), SESSION_A, 4);

		assertThat(result).isEqualTo(SeatHoldResult.LIMIT_EXCEEDED);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 5L)).isNull();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("1", "2", "3", "4");
	}

	@Test
	void 서로_다른_세션이_같은_좌석을_동시에_요청하면_하나만_성공한다() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<SeatHoldResult> first = executor.submit(() -> holdAfterSignal(SESSION_A, ready, start));
			Future<SeatHoldResult> second = executor.submit(() -> holdAfterSignal(SESSION_B, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(SeatHoldResult.SUCCESS, SeatHoldResult.CONFLICT);
			String owner = repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 99L);
			assertThat(owner).isIn(SESSION_A, SESSION_B);
			String loser = SESSION_A.equals(owner) ? SESSION_B : SESSION_A;
			assertThat(redisTemplate.opsForSet().members(sessionSetKey(loser))).isEmpty();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 이전_요청은_새로운_사용자_락을_해제하지_못한다() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		assertThat(repository.tryLockSeatHold(userId, SHOW_SCHEDULE_ID, "old-request")).isTrue();
		assertThat(repository.unlockSeatHold(userId, SHOW_SCHEDULE_ID, "old-request")).isTrue();
		assertThat(repository.tryLockSeatHold(userId, SHOW_SCHEDULE_ID, "new-request")).isTrue();

		assertThat(repository.unlockSeatHold(userId, SHOW_SCHEDULE_ID, "old-request")).isFalse();
		assertThat(redisTemplate.opsForValue().get(seatHoldLockKey(userId))).isEqualTo("new-request");
	}

	@Test
	void 동일_사용자의_동시_좌석락_요청은_하나만_성공한다() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<Boolean> first = executor.submit(() -> lockAfterSignal(userId, "request-a", ready, start));
			Future<Boolean> second = executor.submit(() -> lockAfterSignal(userId, "request-b", ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 소유권_키가_없는_stale_Set_멤버는_다음_선점에서_정리되어_한도에_포함되지_않는다() {
		redisTemplate.opsForSet().add(sessionSetKey(SESSION_A), "stale-seat");

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID,
			List.of(40L, 41L, 42L, 43L),
			SESSION_A,
			4
		);

		assertThat(result).isEqualTo(SeatHoldResult.SUCCESS);
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("40", "41", "42", "43");
	}

	@Test
	void 이전_세션은_새로운_세션이_소유한_좌석을_해제하지_못한다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(50L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		redisTemplate.opsForValue().set(seatHoldKey(50L), SESSION_B);

		boolean released = repository.releaseHeldSeats(SHOW_SCHEDULE_ID, List.of(50L), SESSION_A);

		assertThat(released).isFalse();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 50L)).isEqualTo(SESSION_B);
	}

	@Test
	void 여러_좌석_해제는_하나라도_소유권이_다르면_부분_삭제하지_않는다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(60L, 61L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		redisTemplate.opsForValue().set(seatHoldKey(61L), SESSION_B);

		boolean released = repository.releaseHeldSeats(SHOW_SCHEDULE_ID, List.of(60L, 61L), SESSION_A);

		assertThat(released).isFalse();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 60L)).isEqualTo(SESSION_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 61L)).isEqualTo(SESSION_B);
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("60", "61");
	}

	@Test
	void 예매_claim_중인_좌석도_세션_한도에_포함한다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(70L, 71L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		assertThat(repository.claimHoldSeats(
			SHOW_SCHEDULE_ID, List.of(70L, 71L), SESSION_A, "booking:session-a:reservation:claim"
		)).isTrue();

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID,
			List.of(72L, 73L, 74L),
			SESSION_A,
			4
		);

		assertThat(result).isEqualTo(SeatHoldResult.LIMIT_EXCEEDED);
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("70", "71");
	}

	@Test
	void 복구된_claim에_대한_늦은_해제는_세션_Set을_지우지_못한다() {
		String claim = "booking:session-a:reservation:claim";
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(80L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		assertThat(repository.claimHoldSeats(SHOW_SCHEDULE_ID, List.of(80L), SESSION_A, claim)).isTrue();
		assertThat(repository.restoreClaimedSeats(SHOW_SCHEDULE_ID, List.of(80L), claim, SESSION_A)).isTrue();

		assertThat(repository.releaseClaimedSeats(
			SHOW_SCHEDULE_ID, List.of(80L), SESSION_A, claim
		)).isFalse();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A))).containsExactly("80");
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(81L, 82L, 83L, 84L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.LIMIT_EXCEEDED);
	}

	@Test
	void 예매_claim의_성공_정리와_실패_복구가_세션_Set을_함께_유지한다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(20L, 21L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		assertThat(repository.claimHoldSeats(
			SHOW_SCHEDULE_ID, List.of(20L, 21L), SESSION_A, "booking:session-a:reservation:claim"
		)).isTrue();

		assertThat(repository.restoreClaimedSeats(
			SHOW_SCHEDULE_ID, List.of(20L, 21L), "booking:session-a:reservation:claim", SESSION_A
		)).isTrue();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("20", "21");

		assertThat(repository.claimHoldSeats(
			SHOW_SCHEDULE_ID, List.of(20L, 21L), SESSION_A, "booking:session-a:reservation:second-claim"
		)).isTrue();
		assertThat(repository.releaseClaimedSeats(
			SHOW_SCHEDULE_ID,
			List.of(20L, 21L),
			SESSION_A,
			"booking:session-a:reservation:second-claim"
		)).isTrue();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 20L)).isNull();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 21L)).isNull();
		assertThat(redisTemplate.hasKey(sessionSetKey(SESSION_A))).isFalse();
	}

	@Test
	void 세션_퇴장_정리는_현재_세션_소유인_좌석만_삭제한다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(30L, 31L), SESSION_A, 4))
			.isEqualTo(SeatHoldResult.SUCCESS);
		redisTemplate.opsForValue().set(seatHoldKey(31L), SESSION_B);

		long deleted = repository.releaseSessionHeldSeats(SHOW_SCHEDULE_ID, SESSION_A);

		assertThat(deleted).isEqualTo(1L);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 30L)).isNull();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 31L)).isEqualTo(SESSION_B);
		assertThat(redisTemplate.hasKey(sessionSetKey(SESSION_A))).isFalse();
	}

	private SeatHoldResult holdAfterSignal(
		String sessionToken,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		return repository.holdSeats(SHOW_SCHEDULE_ID, List.of(99L), sessionToken, 4);
	}

	private boolean lockAfterSignal(
		UUID userId,
		String requestToken,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		return repository.tryLockSeatHold(userId, SHOW_SCHEDULE_ID, requestToken);
	}

	private static String sessionSetKey(String sessionToken) {
		return "seat:holds:" + SHOW_SCHEDULE_ID + ":" + sessionToken;
	}

	private static String seatHoldKey(long scheduledSeatId) {
		return "seat:hold:" + SHOW_SCHEDULE_ID + ":" + scheduledSeatId;
	}

	private static String seatHoldLockKey(UUID userId) {
		return "seat:hold:lock:" + SHOW_SCHEDULE_ID + ":" + userId;
	}
}

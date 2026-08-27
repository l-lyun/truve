package org.truve.platform.ticketing.service.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
	private static final String HOLD_A = "hold-a";
	private static final String HOLD_B = "hold-b";

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
	void holdId로_좌석과_meta를_원자적으로_신규_선점한다() {
		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(101L, 102L), SESSION_A, HOLD_A, 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 101L)).isEqualTo(HOLD_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 102L)).isEqualTo(HOLD_A);
		assertThat(redisTemplate.opsForValue().get(holdMetaKey(HOLD_A))).isEqualTo(SESSION_A);
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("101", "102");
	}

	@Test
	void 같은_holdId의_멱등_재시도는_좌석과_meta_TTL을_연장하지_않는다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(103L), SESSION_A, HOLD_A, 4))
			.isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);
		redisTemplate.expire(seatHoldKey(103L), Duration.ofMinutes(5));
		redisTemplate.expire(holdMetaKey(HOLD_A), Duration.ofMinutes(5));

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(103L), SESSION_A, HOLD_A, 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.ALREADY_OWNED);
		assertThat(ttlMillis(seatHoldKey(103L))).isBetween(1L, Duration.ofMinutes(5).toMillis());
		assertThat(ttlMillis(holdMetaKey(HOLD_A))).isBetween(1L, Duration.ofMinutes(5).toMillis());
	}

	@Test
	void 같은_holdId로_완전히_다른_좌석을_요청해도_거절한다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(116L), SESSION_A, HOLD_A, 4))
			.isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(117L), SESSION_A, HOLD_A, 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.CONFLICT);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 116L)).isEqualTo(HOLD_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 117L)).isNull();
	}

	@Test
	void 서로_다른_hold를_합산해_세션당_좌석_한도를_초과할_수_없다() {
		assertThat(repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(118L, 119L, 120L), SESSION_A, HOLD_A, 4
		)).isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);
		assertThat(repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(121L), SESSION_A, HOLD_B, 4
		)).isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(122L), SESSION_A, "hold-c", 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.LIMIT_EXCEEDED);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 122L)).isNull();
		assertThat(redisTemplate.opsForValue().get(holdMetaKey("hold-c"))).isNull();
	}

	@Test
	void 서로_다른_holdId가_같은_좌석을_동시에_요청하면_하나만_신규_선점한다() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<SeatHoldResult> first = executor.submit(() ->
				holdLeaseAfterSignal(SESSION_A, HOLD_A, 123L, ready, start));
			Future<SeatHoldResult> second = executor.submit(() ->
				holdLeaseAfterSignal(SESSION_B, HOLD_B, 123L, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(SeatHoldResult.NEWLY_ACQUIRED, SeatHoldResult.CONFLICT);
			assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 123L)).isIn(HOLD_A, HOLD_B);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 같은_holdId에서_기존_좌석과_빈_좌석이_섞이면_아무것도_추가하지_않는다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(104L), SESSION_A, HOLD_A, 4))
			.isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(104L, 105L), SESSION_A, HOLD_A, 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.CONFLICT);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 104L)).isEqualTo(HOLD_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 105L)).isNull();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A))).containsExactly("104");
	}

	@Test
	void 다른_holdId가_요청_좌석을_소유하면_부분_선점하지_않는다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(106L), SESSION_A, HOLD_A, 4))
			.isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(106L, 107L), SESSION_B, HOLD_B, 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.CONFLICT);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 106L)).isEqualTo(HOLD_A);
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 107L)).isNull();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_B))).isEmpty();
	}

	@Test
	void meta가_다른_세션을_가리키는_stale_Set은_좌석_키가_있어도_한도에서_제외한다() {
		redisTemplate.opsForValue().set(seatHoldKey(108L), HOLD_B);
		redisTemplate.opsForValue().set(holdMetaKey(HOLD_B), SESSION_B);
		redisTemplate.opsForSet().add(sessionSetKey(SESSION_A), "108");

		SeatHoldResult result = repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(109L, 110L, 111L, 112L), SESSION_A, HOLD_A, 4
		);

		assertThat(result).isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A)))
			.containsExactlyInAnyOrder("109", "110", "111", "112");
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 108L)).isEqualTo(HOLD_B);
	}

	@Test
	void 신규_선점_보상은_현재_holdId_소유인_좌석만_삭제한다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(113L, 114L), SESSION_A, HOLD_A, 4))
			.isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);
		redisTemplate.opsForValue().set(seatHoldKey(114L), HOLD_B);

		boolean compensated = repository.compensateNewlyHeldSeats(
			SHOW_SCHEDULE_ID, List.of(113L, 114L), SESSION_A, HOLD_A
		);

		assertThat(compensated).isTrue();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 113L)).isNull();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 114L)).isEqualTo(HOLD_B);
		assertThat(redisTemplate.opsForValue().get(holdMetaKey(HOLD_A))).isNull();
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A))).containsExactly("114");
	}

	@Test
	void meta가_다른_세션이면_보상하지_않는다() {
		assertThat(repository.holdSeats(SHOW_SCHEDULE_ID, List.of(115L), SESSION_A, HOLD_A, 4))
			.isEqualTo(SeatHoldResult.NEWLY_ACQUIRED);
		redisTemplate.opsForValue().set(holdMetaKey(HOLD_A), SESSION_B);

		boolean compensated = repository.compensateNewlyHeldSeats(
			SHOW_SCHEDULE_ID, List.of(115L), SESSION_A, HOLD_A
		);

		assertThat(compensated).isFalse();
		assertThat(repository.getHoldSeatSessionToken(SHOW_SCHEDULE_ID, 115L)).isEqualTo(HOLD_A);
		assertThat(redisTemplate.opsForSet().members(sessionSetKey(SESSION_A))).containsExactly("115");
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

	private SeatHoldResult holdLeaseAfterSignal(
		String sessionToken,
		String holdId,
		Long scheduledSeatId,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		return repository.holdSeats(
			SHOW_SCHEDULE_ID, List.of(scheduledSeatId), sessionToken, holdId, 4
		);
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

	private static String holdMetaKey(String holdId) {
		return "seat:hold:meta:" + holdId;
	}

	private static long ttlMillis(String key) {
		return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
	}
}

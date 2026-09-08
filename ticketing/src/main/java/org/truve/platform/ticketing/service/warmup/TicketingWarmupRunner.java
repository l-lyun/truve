package org.truve.platform.ticketing.service.warmup;

import java.time.Duration;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingQueryService;

import com.truve.platform.common.response.ApiResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ticketing.warmup", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TicketingWarmupProperties.class)
public class TicketingWarmupRunner implements ApplicationRunner {

	private final TicketingWarmupProperties properties;
	private final TicketingQueryService queryService;
	private final JsonMapper jsonMapper;
	private final TransactionTemplate transaction;
	private final LongSupplier nanoTime;
	private volatile Snapshot snapshot = new Snapshot(State.NOT_STARTED, 0, Duration.ZERO);

	@Autowired
	public TicketingWarmupRunner(TicketingWarmupProperties properties, TicketingQueryService queryService,
		JsonMapper jsonMapper, PlatformTransactionManager transactionManager) {
		this(properties, queryService, jsonMapper, transactionManager, System::nanoTime);
	}

	TicketingWarmupRunner(TicketingWarmupProperties properties, TicketingQueryService queryService,
		JsonMapper jsonMapper, PlatformTransactionManager transactionManager, LongSupplier nanoTime) {
		this.properties = properties;
		this.queryService = queryService;
		this.jsonMapper = jsonMapper;
		this.nanoTime = nanoTime;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setReadOnly(true);
		this.transaction.setTimeout(properties.queryTimeoutSeconds());
	}

	@Override
	public void run(ApplicationArguments args) {
		long started = nanoTime.getAsLong();
		int completed = 0;
		snapshot = new Snapshot(State.RUNNING, 0, Duration.ZERO);
		log.info("좌석 조회 웜업 시작. iterations={}", properties.iterations());
		try {
			for (int i = 0; i < properties.iterations(); i++) {
				checkBudget(started);
				TicketingResponse.Seats seats = transaction.execute(
					status -> queryService.getSeats(properties.showScheduleId()));
				if (seats == null || seats.getSections().isEmpty()) {
					throw new IllegalStateException("웜업 회차에 좌석 데이터가 없습니다.");
				}
				jsonMapper.writeValueAsBytes(ApiResult.ok(seats));
				completed++;
				checkBudget(started);
			}
			snapshot = new Snapshot(State.COMPLETED, completed, elapsed(started));
			log.info("좌석 조회 웜업 완료. iterations={}, durationMs={}", completed, snapshot.duration().toMillis());
		} catch (RuntimeException exception) {
			snapshot = new Snapshot(State.FAILED, completed, elapsed(started));
			log.error("좌석 조회 웜업 실패. iterations={}, durationMs={}, cause={}",
				completed, snapshot.duration().toMillis(), exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private void checkBudget(long started) {
		// 실행 중인 JDBC 호출을 강제로 중단하지 않는다. 드라이버 연결/socket timeout은 별도 설정한다.
		if (elapsed(started).compareTo(properties.maxDuration()) >= 0) {
			throw new IllegalStateException("웜업 실행 예산을 초과했습니다.");
		}
	}

	private Duration elapsed(long started) {
		return Duration.ofNanos(nanoTime.getAsLong() - started);
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	public enum State { NOT_STARTED, RUNNING, COMPLETED, FAILED }

	public record Snapshot(State state, int completedIterations, Duration duration) { }
}

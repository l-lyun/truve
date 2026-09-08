package org.truve.platform.ticketing.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingQueryService;

import tools.jackson.databind.json.JsonMapper;

class TicketingWarmupRunnerTest {

	private final TicketingQueryService queryService = mock(TicketingQueryService.class);
	private final JsonMapper mapper = spy(JsonMapper.builder().build());
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
	private final AtomicLong nanos = new AtomicLong();
	private final TicketingWarmupProperties properties =
		new TicketingWarmupProperties(true, 1L, 3, Duration.ofSeconds(30), 5);
	private TicketingWarmupRunner runner;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
		runner = new TicketingWarmupRunner(properties, queryService, mapper, transactionManager, nanos::get);
	}

	@Test
	void 반복마다_읽기전용_트랜잭션으로_조회한_뒤_응답을_직렬화한다() {
		when(queryService.getSeats(1L)).thenReturn(seats());
		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.NOT_STARTED);

		runner.run(new DefaultApplicationArguments());

		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.COMPLETED);
		assertThat(runner.snapshot().completedIterations()).isEqualTo(3);
		var order = inOrder(queryService, mapper, transactionManager);
		for (int i = 0; i < 3; i++) {
			order.verify(transactionManager).getTransaction(argThat(definition ->
				definition.isReadOnly() && definition.getTimeout() == 5
					&& definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
			order.verify(queryService).getSeats(1L);
			order.verify(transactionManager).commit(any());
			order.verify(mapper).writeValueAsBytes(any());
		}
	}

	@Test
	void 조회_실패는_rollback하고_기동_호출자에게_전파한다() {
		IllegalStateException failure = new IllegalStateException("조회 실패");
		when(queryService.getSeats(1L)).thenThrow(failure);

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isSameAs(failure);
		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.FAILED);
		assertThat(runner.snapshot().completedIterations()).isZero();
		verify(transactionManager).rollback(any());
		verify(transactionManager, never()).commit(any());
		verifyNoInteractions(mapper);
	}

	@Test
	void 빈_좌석은_웜업_성공으로_취급하지_않는다() {
		when(queryService.getSeats(1L)).thenReturn(new TicketingResponse.Seats(List.of()));
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("좌석 데이터");
		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.FAILED);
		verifyNoInteractions(mapper);
	}

	@Test
	void 직렬화_실패를_숨기지_않는다() {
		when(queryService.getSeats(1L)).thenReturn(seats());
		IllegalStateException failure = new IllegalStateException("직렬화 실패");
		doThrow(failure).when(mapper).writeValueAsBytes(any());
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isSameAs(failure);
		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.FAILED);
		assertThat(runner.snapshot().completedIterations()).isZero();
		verify(queryService).getSeats(1L);
	}

	@Test
	void 마지막_반복이라도_실행_예산을_넘으면_실패한다() {
		when(queryService.getSeats(1L)).thenAnswer(invocation -> {
			nanos.addAndGet(Duration.ofSeconds(10).toNanos());
			return seats();
		});
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("실행 예산");
		assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.FAILED);
		assertThat(runner.snapshot().completedIterations()).isEqualTo(3);
		assertThat(runner.snapshot().duration()).isEqualTo(Duration.ofSeconds(30));
	}

	private static TicketingResponse.Seats seats() {
		return TicketingResponse.Seats.from(List.of(
			new SeatSectionsDto(1L, "VIP", "VIP", 100000L, 10L, "A", 1L, SeatStatus.AVAILABLE)));
	}
}

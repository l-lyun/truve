package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationResult;
import org.truve.platform.ticketing.service.booking.service.HoldReservationCreationService.HoldReservationCommand;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository.SeatHoldResult;

@ExtendWith(MockitoExtension.class)
class SeatHoldSagaServiceTest {
	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final Long SHOW_SCHEDULE_ID = 100L;
	private static final List<Long> SEAT_IDS = List.of(10L, 11L);
	private static final String SESSION_TOKEN = "session-token";
	private static final String IDEMPOTENCY_KEY = "request-001";

	@Mock
	private TicketingService ticketingService;
	@Mock
	private TicketingSecurityService ticketingSecurityService;
	@Mock
	private SeatHoldLockService seatHoldLockService;
	@Mock
	private TicketingRedisRepository redisRepository;
	@Mock
	private ScheduledSeatRepository scheduledSeatRepository;
	@Mock
	private ShowScheduledRepository showScheduledRepository;
	@Mock
	private HoldReservationCreationService creationService;
	@InjectMocks
	private SeatHoldSagaService service;

	private SeatHoldLockService.SeatHoldLock lock;

	@BeforeEach
	void setUp() {
		lock = new SeatHoldLockService.SeatHoldLock(USER_ID, SHOW_SCHEDULE_ID, "lock-token");
		given(seatHoldLockService.acquire(USER_ID, SHOW_SCHEDULE_ID)).willReturn(lock);
	}

	@Test
	void 신규_Redis_선점과_DB_접수가_성공하면_주문을_반환하고_lease를_유지한다() {
		stubNewRequest(SeatHoldResult.NEWLY_ACQUIRED);
		HoldReservationResult created = result("R-001");
		given(creationService.create(any())).willReturn(created);

		var response = service.hold(
			SHOW_SCHEDULE_ID, USER_ID, SESSION_TOKEN, IDEMPOTENCY_KEY, SEAT_IDS);

		assertThat(response.getReservationNumber()).isEqualTo("R-001");
		assertThat(response.getStatus()).isEqualTo(ReservationStatus.HOLD_PENDING);
		verify(redisRepository, never()).compensateNewlyHeldSeats(any(), any(), anyString(), anyString());
		verify(seatHoldLockService).release(lock);
	}

	@Test
	void 기존_holdId_주문이_있으면_Redis를_다시_쓰지_않고_같은_주문을_반환한다() {
		given(creationService.findExisting(anyString(), anyString(), any(), any()))
			.willReturn(Optional.of(result("R-001")));

		var response = service.hold(
			SHOW_SCHEDULE_ID, USER_ID, SESSION_TOKEN, IDEMPOTENCY_KEY, SEAT_IDS);

		assertThat(response.getReservationNumber()).isEqualTo("R-001");
		verify(redisRepository, never()).holdSeats(any(), any(), anyString(), anyString(), any(Integer.class));
		verify(creationService, never()).create(any());
		verify(seatHoldLockService).release(lock);
	}

	@Test
	void ALREADY_OWNED_재시도는_남은_Redis_TTL을_주문_만료시각으로_사용한다() {
		stubNewRequest(SeatHoldResult.ALREADY_OWNED);
		given(creationService.create(any())).willReturn(result("R-001"));
		LocalDateTime before = LocalDateTime.now();

		service.hold(SHOW_SCHEDULE_ID, USER_ID, SESSION_TOKEN, IDEMPOTENCY_KEY, SEAT_IDS);

		ArgumentCaptor<HoldReservationCommand> captor = ArgumentCaptor.forClass(HoldReservationCommand.class);
		verify(creationService).create(captor.capture());
		assertThat(captor.getValue().expiresAt())
			.isAfter(before.plusMinutes(7))
			.isBefore(before.plusMinutes(9));
	}

	@Test
	void NEWLY_ACQUIRED_후_DB가_실패하면_자신의_Redis_lease만_보상한다() {
		stubNewRequest(SeatHoldResult.NEWLY_ACQUIRED);
		RuntimeException failure = new RuntimeException("db failure");
		given(creationService.create(any())).willThrow(failure);
		given(redisRepository.compensateNewlyHeldSeats(any(), any(), anyString(), anyString())).willReturn(true);

		assertThatThrownBy(() -> service.hold(
			SHOW_SCHEDULE_ID, USER_ID, SESSION_TOKEN, IDEMPOTENCY_KEY, SEAT_IDS))
			.isSameAs(failure);

		verify(redisRepository).compensateNewlyHeldSeats(
			any(), any(), anyString(), anyString());
		verify(seatHoldLockService).release(lock);
	}

	@Test
	void ALREADY_OWNED_후_DB가_실패하면_기존_Redis_lease를_보상하지_않는다() {
		stubNewRequest(SeatHoldResult.ALREADY_OWNED);
		RuntimeException failure = new RuntimeException("db failure");
		given(creationService.create(any())).willThrow(failure);

		assertThatThrownBy(() -> service.hold(
			SHOW_SCHEDULE_ID, USER_ID, SESSION_TOKEN, IDEMPOTENCY_KEY, SEAT_IDS))
			.isSameAs(failure);

		verify(redisRepository, never()).compensateNewlyHeldSeats(any(), any(), anyString(), anyString());
		verify(seatHoldLockService).release(lock);
	}

	@Test
	void DB_커밋_여부_재조회도_실패하면_Redis_lease를_보상하지_않는다() {
		stubNewRequest(SeatHoldResult.NEWLY_ACQUIRED);
		RuntimeException dbFailure = new RuntimeException("commit result unknown");
		RuntimeException lookupFailure = new RuntimeException("db unavailable");
		given(creationService.create(any())).willThrow(dbFailure);
		given(creationService.findExisting(anyString(), anyString(), any(), any()))
			.willReturn(Optional.empty())
			.willThrow(lookupFailure);

		assertThatThrownBy(() -> service.hold(
			SHOW_SCHEDULE_ID, USER_ID, SESSION_TOKEN, IDEMPOTENCY_KEY, SEAT_IDS))
			.isSameAs(dbFailure);

		verify(redisRepository, never()).compensateNewlyHeldSeats(any(), any(), anyString(), anyString());
		assertThat(dbFailure.getSuppressed()).containsExactly(lookupFailure);
	}

	private void stubNewRequest(SeatHoldResult result) {
		given(creationService.findExisting(anyString(), anyString(), any(), any())).willReturn(Optional.empty());
		given(showScheduledRepository.existsById(SHOW_SCHEDULE_ID)).willReturn(true);
		ScheduledSeat first = org.mockito.Mockito.mock(ScheduledSeat.class);
		ScheduledSeat second = org.mockito.Mockito.mock(ScheduledSeat.class);
		given(first.getShowScheduleId()).willReturn(SHOW_SCHEDULE_ID);
		given(second.getShowScheduleId()).willReturn(SHOW_SCHEDULE_ID);
		given(first.isAvailable()).willReturn(true);
		given(second.isAvailable()).willReturn(true);
		given(scheduledSeatRepository.findAllById(SEAT_IDS)).willReturn(List.of(first, second));
		given(redisRepository.holdSeats(
			eq(SHOW_SCHEDULE_ID), eq(SEAT_IDS), eq(SESSION_TOKEN), anyString(), eq(4))).willReturn(result);
		if (result == SeatHoldResult.ALREADY_OWNED) {
			given(redisRepository.getHoldTtlMillis(anyString())).willReturn(8 * 60 * 1000L);
		}
	}

	private HoldReservationResult result(String reservationNumber) {
		return new HoldReservationResult(
			reservationNumber,
			ReservationStatus.HOLD_PENDING,
			LocalDateTime.of(2026, 8, 27, 17, 0)
		);
	}
}

package org.truve.platform.ticketing.service.ticketing.external.kafka;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedEventHandler;
import org.truve.platform.ticketing.service.ticketing.service.ScheduledSeatStatusService;

import com.truve.platform.common.support.JsonConverter;

@ExtendWith(MockitoExtension.class)
class BookingConsumerTest {

	@Mock
	private JsonConverter jsonConverter;

	@Mock
	private ScheduledSeatStatusService scheduledSeatStatusService;
	@Mock
	private HoldRequestedEventHandler holdRequestedEventHandler;

	@InjectMocks
	private BookingConsumer bookingConsumer;

	@Test
	@DisplayName("HOLD_REQUESTED 이벤트면 비동기 좌석 HOLD handler에 위임한다.")
	void holdRequested이벤트_소비성공() {
		TicketingEventCommand.HoldRequested event = TicketingEventCommand.HoldRequested.of(
			"H-001", "R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			"session-token", 1L, List.of(10L, 11L),
			java.time.LocalDateTime.of(2026, 8, 27, 18, 0)
		);
		given(jsonConverter.convert("payload", TicketingEventCommand.HoldRequested.class)).willReturn(event);

		bookingConsumer.consume("payload", "HOLD_REQUESTED");

		verify(holdRequestedEventHandler).handle(event);
	}

	@Test
	@DisplayName("HOLD_RELEASED 이벤트면 좌석 해제 서비스에 위임한다.")
	void release이벤트_소비성공() {
		TicketingEventCommand.HoldReleased event = new TicketingEventCommand.HoldReleased(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		given(jsonConverter.convert("payload", TicketingEventCommand.HoldReleased.class)).willReturn(event);

		bookingConsumer.consume("payload", "HOLD_RELEASED");

		verify(jsonConverter).convert("payload", TicketingEventCommand.HoldReleased.class);
		verify(scheduledSeatStatusService).releaseSeats(event);
	}

	@Test
	@DisplayName("SOLD_CONFIRMED 이벤트면 좌석 구매 서비스에 위임한다.")
	void sold이벤트_소비성공() {
		TicketingEventCommand.SoldConfirmed event = new TicketingEventCommand.SoldConfirmed(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		given(jsonConverter.convert("payload", TicketingEventCommand.SoldConfirmed.class)).willReturn(event);

		bookingConsumer.consume("payload", "SOLD_CONFIRMED");

		verify(jsonConverter).convert("payload", TicketingEventCommand.SoldConfirmed.class);
		verify(scheduledSeatStatusService).purchaseSeats(event);
	}

	@Test
	@DisplayName("SALE_CANCELED 이벤트면 판매 취소 서비스에 위임한다.")
	void saleCanceled이벤트_소비성공() {
		TicketingEventCommand.SaleCanceled event = new TicketingEventCommand.SaleCanceled(
			"R-001",
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			List.of(10L, 11L)
		);
		given(jsonConverter.convert("payload", TicketingEventCommand.SaleCanceled.class)).willReturn(event);

		bookingConsumer.consume("payload", "SALE_CANCELED");

		verify(jsonConverter).convert("payload", TicketingEventCommand.SaleCanceled.class);
		verify(scheduledSeatStatusService).cancelSales(event);
	}

	@Test
	@DisplayName("알 수 없는 이벤트 타입이면 아무 작업도 하지 않는다.")
	void 알수없는이벤트_무시() {
		bookingConsumer.consume("payload", "UNKNOWN");

		verifyNoInteractions(jsonConverter, scheduledSeatStatusService, holdRequestedEventHandler);
	}
}

package org.truve.platform.ticketing.service.booking.inbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.inbox.repository.PaymentEventInboxRepository;
import org.truve.platform.ticketing.service.booking.service.BookingService;

@DataJpaTest
@Import({PaymentEventInboxHandler.class, PaymentEventProcessor.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentEventInboxIntegrationTest {
	@Autowired
	private PaymentEventInboxHandler handler;
	@Autowired
	private PaymentEventInboxRepository inboxRepository;
	@MockitoBean
	private BookingService bookingService;

	@BeforeEach
	void cleanInbox() {
		inboxRepository.deleteAll();
	}

	@Test
	void 동일_eventId는_Inbox에_한_번만_기록하고_비즈니스_로직도_한_번만_실행한다() {
		UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingEventCommand.Confirmed event = confirmedEvent();

		handler.handle(eventId, "CONFIRMED", event);
		handler.handle(eventId, "CONFIRMED", event);

		assertThat(inboxRepository.count()).isEqualTo(1L);
		verify(bookingService, times(1)).confirm(event);
	}

	@Test
	void 비즈니스_처리가_실패하면_Inbox도_같이_롤백한다() {
		UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
		BookingEventCommand.Confirmed event = confirmedEvent();
		doThrow(new IllegalStateException("transition failed")).when(bookingService).confirm(event);

		assertThatThrownBy(() -> handler.handle(eventId, "CONFIRMED", event))
			.isInstanceOf(IllegalStateException.class);

		assertThat(inboxRepository.existsByEventId(eventId)).isFalse();
	}

	@Test
	void 동일_eventId에_다른_예약번호가_오면_중복으로_숨기지_않는다() {
		UUID eventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
		handler.handle(eventId, "CONFIRMED", confirmedEvent());
		BookingEventCommand.Confirmed conflicting = new BookingEventCommand.Confirmed(
			"R-OTHER", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);

		assertThatThrownBy(() -> handler.handle(eventId, "CONFIRMED", conflicting))
			.isInstanceOf(IllegalStateException.class);

		assertThat(inboxRepository.count()).isEqualTo(1L);
	}

	private BookingEventCommand.Confirmed confirmedEvent() {
		return new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);
	}
}

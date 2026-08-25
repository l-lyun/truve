package org.truve.platform.ticketing.service.booking.external.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.inbox.service.PaymentEventInboxHandler;

import com.truve.platform.common.support.JsonConverter;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {
	@Mock
	private JsonConverter jsonConverter;
	@Mock
	private PaymentEventInboxHandler paymentEventInboxHandler;

	@InjectMocks
	private PaymentConsumer paymentConsumer;

	@Test
	void 결제완료_이벤트의_eventId와_예약정보를_Inbox_Handler에_전달한다() {
		UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingEventCommand.Confirmed event = new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);
		given(jsonConverter.convert("{}", BookingEventCommand.Confirmed.class)).willReturn(event);

		paymentConsumer.consume("{}", "CONFIRMED", eventId.toString());

		verify(paymentEventInboxHandler).handle(eventId, "CONFIRMED", event);
	}

	@Test
	void 잘못된_eventId는_임의값으로_대체하지_않고_실패한다() {
		assertThatThrownBy(() -> paymentConsumer.consume("{}", "CONFIRMED", "not-a-uuid"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}

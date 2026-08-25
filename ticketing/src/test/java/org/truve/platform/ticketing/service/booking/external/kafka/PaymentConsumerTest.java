package org.truve.platform.ticketing.service.booking.external.kafka;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.service.BookingService;

import com.truve.platform.common.support.JsonConverter;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {
	@Mock
	private JsonConverter jsonConverter;
	@Mock
	private BookingService bookingService;

	@InjectMocks
	private PaymentConsumer paymentConsumer;

	@Test
	void 결제완료_이벤트를_변환해_예매_서비스에_전달한다() {
		BookingEventCommand.Confirmed event = new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);
		given(jsonConverter.convert("{}", BookingEventCommand.Confirmed.class)).willReturn(event);

		paymentConsumer.consume("{}", "CONFIRMED");

		verify(bookingService).confirm(event);
	}

	@Test
	void 입금완료_이벤트를_변환해_예매_서비스에_전달한다() {
		BookingEventCommand.DepositReceived event = new BookingEventCommand.DepositReceived(
			"R-001", LocalDateTime.now()
		);
		given(jsonConverter.convert("{}", BookingEventCommand.DepositReceived.class)).willReturn(event);

		paymentConsumer.consume("{}", "DEPOSIT_RECEIVED");

		verify(bookingService).depositReceive(event);
	}
}

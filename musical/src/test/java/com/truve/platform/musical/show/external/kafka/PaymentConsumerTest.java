package com.truve.platform.musical.show.external.kafka;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.support.JsonConverter;
import com.truve.platform.musical.show.service.MembershipService;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

	@Mock
	private MembershipService membershipService;

	private PaymentConsumer paymentConsumer;

	@BeforeEach
	void setUp() {
		paymentConsumer = new PaymentConsumer(new JsonConverter(new ObjectMapper()), membershipService);
	}

	@Test
	@DisplayName("CONFIRMED 이벤트를 수신하면 멤버십 결제 완료를 처리한다.")
	void confirmed_이벤트_처리() {
		paymentConsumer.consume("{\"orderId\":\"M20260402123456\"}", "CONFIRMED");

		verify(membershipService).confirm("M20260402123456");
	}

	@Test
	@DisplayName("DEPOSIT_RECEIVED 이벤트를 수신하면 멤버십 입금 완료를 처리한다.")
	void depositReceived_이벤트_처리() {
		paymentConsumer.consume("{\"orderId\":\"M20260402123456\"}", "DEPOSIT_RECEIVED");

		verify(membershipService).depositReceive("M20260402123456");
	}

	@Test
	@DisplayName("알 수 없는 이벤트는 무시한다.")
	void unknown_이벤트_무시() {
		paymentConsumer.consume("{\"orderId\":\"M20260402123456\"}", "UNKNOWN");

		verify(membershipService, never()).confirm(org.mockito.Mockito.anyString());
		verify(membershipService, never()).depositReceive(org.mockito.Mockito.anyString());
	}
}

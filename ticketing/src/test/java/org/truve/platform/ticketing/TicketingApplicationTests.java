package org.truve.platform.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.truve.platform.ticketing.service.TicketingApplication;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentClient;

@SpringBootTest(classes = TicketingApplication.class)
class TicketingApplicationTests {

	@MockitoBean
	PaymentClient paymentClient;

	@Test
	void contextLoads() {
	}

}

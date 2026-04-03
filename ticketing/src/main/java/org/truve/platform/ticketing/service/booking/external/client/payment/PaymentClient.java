package org.truve.platform.ticketing.service.booking.external.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.truve.platform.ticketing.service.booking.config.PaymentFeignConfig;

@FeignClient(name = "payment-service", configuration = PaymentFeignConfig.class)
public interface PaymentClient {

	@PostMapping("/api/payments/{orderId}/cancel")
	void cancel(
		@PathVariable String orderId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody PaymentRequest.Cancel request
	);
}
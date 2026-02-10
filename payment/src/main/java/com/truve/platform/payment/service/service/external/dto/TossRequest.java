package com.truve.platform.payment.service.service.external.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TossRequest {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirm {
		private String orderId;
		private Long amount;
		private String paymentKey;
	}

}

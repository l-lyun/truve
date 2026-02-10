package com.truve.platform.payment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class PaymentResponse {

	@Getter
	@AllArgsConstructor
	public static class Create {
		Long paymentId;
	}
}

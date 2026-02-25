package com.truve.platform.payment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class WebhookRequest {

	@Getter
	@AllArgsConstructor
	public static class Deposit {
		String createdAt;
		String secret;
		String status;
		String transactionKey;
		String orderId;
	}
}

package com.truve.platform.payment.service.event;

import com.truve.platform.payment.service.dto.WebhookRequest;

public record WebhookEvent(
	String createdAt,
	String status,
	String orderId
) {
	public static WebhookEvent from(WebhookRequest.Deposit request) {
		return new WebhookEvent(
			request.getCreatedAt(),
			request.getStatus(),
			request.getOrderId()
		);
	}
}

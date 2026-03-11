package com.truve.platform.payment.service.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.truve.platform.payment.service.service.PaymentService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebhookEventListener {

	private final PaymentService paymentService;

	@Async
	@EventListener
	public void handleWebhook(WebhookEvent event) {
		switch (event.status()) {
			case "DONE" -> paymentService.completeDeposit(event.orderId(), event.createdAt());
			case "CANCELED" -> handleCanceled(event);
		}
	}

	private void handleCanceled(WebhookEvent event) {

	}
}

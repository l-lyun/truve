package com.truve.platform.payment.service.controller;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.payment.service.dto.WebhookRequest;
import com.truve.platform.payment.service.event.WebhookEvent;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks")
@Hidden
public class WebhookController {
	private final ApplicationEventPublisher eventPublisher;

	@PostMapping("/deposit")
	public ResponseEntity<Void> handleDepositWebhook(@RequestBody WebhookRequest.Deposit request) {
		eventPublisher.publishEvent(WebhookEvent.from(request));
		return ResponseEntity.ok().build();
	}
}

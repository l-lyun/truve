package com.truve.platform.payment.service.controller;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {
	private final PaymentService paymentService;

	@Value("${app.frontend.success-url}")
	private String successUrl;

	@Value("${app.frontend.fail-url}")
	private String failUrl;

	@Operation(summary = "결제 생성", description = "Toss Payments에 결제를 요청하기 전에 호출해 주세요")
	@PostMapping
	public ApiResult<PaymentResponse.Create> create(@RequestBody @Valid PaymentRequest.Create request) {
		Long paymentId = paymentService.create(request);
		var response = new PaymentResponse.Create(paymentId);

		return ApiResult.ok(response);
	}

	@Operation(summary = "결제 승인",
		description = "Toss Payments에서 결제 요청 승인 후 successUrl로 호출하는 API입니다. 프론트엔드의 성공 페이지로 orderId를 담아 리다이렉트 합니다.")
	@GetMapping("/confirm")
	@ApiResponse(responseCode = "302")
	public ResponseEntity<Void> confirm(
		@RequestParam String orderId,
		@RequestParam String paymentKey,
		@RequestParam Long amount
	) {
		paymentService.confirm(orderId, paymentKey, amount);

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(successUrl + "?orderId=" + orderId))
			.build();
	}

	@Operation(summary = "결제 실패",
		description = "Toss Payments에서 결제 요청이 실패했을 때 failUrl로 호출하는 API입니다. 프론트엔드의 실패 페이지로 code, message, orderId를 담아 리다이렉트합니다.")
	@GetMapping("/fail")
	@ApiResponse(responseCode = "302")
	public ResponseEntity<Void> fail(
		@RequestParam String code,
		@RequestParam String message,
		@RequestParam String orderId
	) {
		String redirectUrl = UriComponentsBuilder.fromPath(failUrl)
			.queryParam("code", code)
			.queryParam("message", message)
			.queryParam("orderId", orderId)
			.build()
			.toUriString();

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(redirectUrl))
			.build();
	}
}

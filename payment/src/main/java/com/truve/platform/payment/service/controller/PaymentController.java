package com.truve.platform.payment.service.controller;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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

	@Operation(summary = "결제 정보 상세 조회", description = "주문 ID로 결제 정보를 조회합니다.")
	@GetMapping("/{orderId}")
	public ApiResult<PaymentResponse.Details> details(@PathVariable String orderId) {
		return ApiResult.ok(paymentService.details(orderId));
	}

	@Operation(summary = "은행 리스트 조회", description = "은행 이름, 코드 리스트를 조회합니다.")
	@GetMapping("/banks")
	public ApiResult<List<PaymentResponse.Bank>> getBankList() {
		return ApiResult.ok(paymentService.getBankList());
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

	@Operation(summary = "결제 취소",
		description = "주문 ID로 결제를 취소합니다. 무통장입금 결제 취소시 RefundReceiveAccount를 필수로 입력해 주세요. ",
		parameters = {
			@Parameter(
				name = "Idempotency-Key",
				description = "중복 요청 방지를 위한 고유 키입니다. 임의의 중복되지 않는 UUID 값을 입력해 주세요.",
				required = true,
				in = ParameterIn.HEADER,
				schema = @Schema(type = "string", format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
			)
		}
	)
	@PostMapping("/{orderId}/cancel")
	public ApiResult<PaymentResponse.Cancel> cancel(
		@PathVariable String orderId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid PaymentRequest.Cancel request
	) {
		return ApiResult.ok(paymentService.cancel(orderId, idempotencyKey, request));
	}

}

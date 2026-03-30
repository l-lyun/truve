package com.truve.platform.payment.service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {
	private final PaymentService paymentService;

	@Operation(summary = "은행 리스트 조회", description = "은행 이름, 코드 리스트를 조회합니다.")
	@GetMapping("/banks")
	public ApiResult<List<PaymentResponse.Bank>> getBankList() {
		return ApiResult.ok(paymentService.getBankList());
	}

	@Operation(summary = "결제 승인", description = "결제를 승인 처리합니다.")
	@PostMapping("/confirm")
	public ApiResult<PaymentResponse.OrderId> confirm(@RequestBody @Valid PaymentRequest.Confirm request) {
		return ApiResult.ok(paymentService.confirm(request));
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
	public ApiResult<Void> cancel(
		@PathVariable String orderId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid PaymentRequest.Cancel request
	) {
		paymentService.cancel(orderId, idempotencyKey, request);
		return ApiResult.ok();
	}

}

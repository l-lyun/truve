package com.truve.platform.payment.service.dto;

import com.truve.platform.payment.service.domain.constant.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class PaymentRequest {

	@Getter
	@AllArgsConstructor
	public static class Create {
		@NotBlank
		private String orderId;
		@NotNull
		@Positive
		private Long amount;
		@NotNull
		private PaymentMethod method;
	}
}

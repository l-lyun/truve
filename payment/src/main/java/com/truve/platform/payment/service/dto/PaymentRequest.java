package com.truve.platform.payment.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class PaymentRequest {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirm {
		@NotBlank
		private String orderId;
		@NotBlank
		private String paymentKey;
		@NotNull
		@Positive
		private Long amount;
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Cancel {
		@NotNull
		private String cancelReason;
		@NotNull
		@Positive
		private Long cancelAmount;
		private RefundReceiveAccount refundReceiveAccount;
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class RefundReceiveAccount {
		private String bankCode;
		private String accountNumber;
		private String holderName;
	}
}

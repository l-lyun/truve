package com.truve.platform.payment.service.external.client;

import com.truve.platform.payment.service.dto.PaymentRequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TossRequest {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirm {
		private String orderId;
		private Long amount;
		private String paymentKey;

		public static Confirm of(PaymentRequest.Confirm request) {
			return new Confirm(
				request.getOrderId(),
				request.getAmount(),
				request.getPaymentKey()
			);
		}
	}

	@Getter
	@Builder
	public static class Cancel {
		private String cancelReason;
		private Long cancelAmount;
		private RefundReceiveAccount refundReceiveAccount;

		public static Cancel from(PaymentRequest.Cancel request) {
			return Cancel.builder()
				.cancelReason(request.getCancelReason())
				.cancelAmount(request.getCancelAmount())
				.refundReceiveAccount(request.getRefundReceiveAccount() == null ? null :
					RefundReceiveAccount.from(request.getRefundReceiveAccount()))
				.build();
		}
	}

	@Getter
	@Builder
	public static class RefundReceiveAccount {
		private String bank;
		private String accountNumber;
		private String holderName;

		public static RefundReceiveAccount from(PaymentRequest.RefundReceiveAccount request) {
			return RefundReceiveAccount.builder()
				.bank(request.getBankCode())
				.accountNumber(request.getAccountNumber())
				.holderName(request.getHolderName())
				.build();
		}
	}

}

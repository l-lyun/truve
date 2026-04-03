package org.truve.platform.ticketing.service.booking.external.client.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class PaymentRequest {
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

		public static Cancel of(String cancelReason, Long cancelAmount) {
			return new Cancel(cancelReason, cancelAmount, null);
		}
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

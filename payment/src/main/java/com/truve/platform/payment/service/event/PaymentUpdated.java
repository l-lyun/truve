package com.truve.platform.payment.service.event;

import java.time.LocalDateTime;

import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.domain.entity.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class PaymentUpdated {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirmed {
		private String orderId;
		private LocalDateTime approvedAt;
		private PaymentStatus status;

		public static Confirmed of(Payment payment) {
			return new Confirmed(
				payment.getOrderId(),
				payment.getApprovedAt(),
				payment.getStatus()
			);
		}
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class DepositReceived {
		private String orderId;
		private LocalDateTime approvedAt;

		public static DepositReceived of(Payment payment) {
			return new DepositReceived(
				payment.getOrderId(),
				payment.getApprovedAt()
			);
		}
	}
}

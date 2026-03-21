package com.truve.platform.payment.service.event;

import java.time.LocalDateTime;

import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.domain.entity.VirtualAccount;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class PaymentUpdated {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirmed {
		private String orderId;
		private LocalDateTime requestedAt;
		private LocalDateTime approvedAt;
		private PaymentMethod method;
		private VirtualAccount virtualAccount;
		private PaymentStatus status;

		public static Confirmed of(Payment payment) {
			return new Confirmed(
				payment.getOrderId(),
				payment.getRequestedAt(),
				payment.getApprovedAt(),
				payment.getMethod(),
				payment.getVirtualAccount(),
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

package com.truve.platform.payment.service.external.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.truve.platform.payment.service.domain.constant.PaymentMethod;

import lombok.Getter;
import lombok.NoArgsConstructor;

public class TossResponse {

	@Getter
	@NoArgsConstructor
	public static class Payment {
		private String paymentKey;
		private String orderId;
		private String method;
		private Long totalAmount;
		private Long balanceAmount;
		private String status;
		private String approvedAt;
		private String requestedAt;

		private Receipt receipt;
		private Card card;
		private EasyPay easyPay;
		private VirtualAccount virtualAccount;

		private List<Cancel> cancels;

		@JsonAnySetter
		private Map<String, Object> others = new HashMap<>();

		public Object getMethodDetailsEntity() {
			return switch (getPaymentMethod()) {
				case CARD -> card != null ? card.toEntity() : null;
				case EASY_PAY -> easyPay != null ? easyPay.toEntity() : null;
				case VIRTUAL_ACCOUNT -> virtualAccount != null ? virtualAccount.toEntity() : null;
				default -> null;
			};
		}

		private PaymentMethod getPaymentMethod() {
			return PaymentMethod.of(method);
		}
	}

	@Getter
	@NoArgsConstructor
	public static class Receipt {
		private String url;
	}

	@Getter
	@NoArgsConstructor
	public static class Card {
		private String issuerCode;
		private String number;
		private Integer installmentPlanMonths;

		public com.truve.platform.payment.service.domain.entity.Card toEntity() {
			return com.truve.platform.payment.service.domain.entity.Card.builder()
				.issuerCode(issuerCode)
				.number(number)
				.installmentPlanMonths(installmentPlanMonths)
				.build();
		}
	}

	@Getter
	@NoArgsConstructor
	public static class EasyPay {
		private String provider;
		private Long discountAmount;

		public com.truve.platform.payment.service.domain.entity.EasyPay toEntity() {
			return com.truve.platform.payment.service.domain.entity.EasyPay.builder()
				.provider(provider)
				.discountAmount(discountAmount)
				.build();
		}
	}

	@Getter
	@NoArgsConstructor
	public static class VirtualAccount {
		private String accountNumber;
		private String bankCode;
		private String customerName;
		private String dueDate;

		public com.truve.platform.payment.service.domain.entity.VirtualAccount toEntity() {
			return com.truve.platform.payment.service.domain.entity.VirtualAccount.builder()
				.accountNumber(this.accountNumber)
				.bankCode(this.bankCode)
				.customerName(this.customerName)
				.dueDate(LocalDateTime.parse(this.dueDate, DateTimeFormatter.ISO_DATE_TIME))
				.build();
		}
	}

	@Getter
	@NoArgsConstructor
	public static class Cancel {
		private Long cancelAmount;
		private String cancelReason;
		private Long refundableAmount;
		private String canceledAt;
		private String transactionKey;
		private String cancelStatus;
	}

	@Getter
	@NoArgsConstructor
	public static class Error {
		private String code;
		private String message;
	}
}
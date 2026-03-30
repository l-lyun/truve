package com.truve.platform.payment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class PaymentResponse {

	@Getter
	@Builder
	public static class Bank {
		private final String bankCode;
		private final String bankName;

		public static Bank from(com.truve.platform.payment.service.domain.constant.Bank bank) {
			return Bank.builder()
				.bankCode(bank.getBankCode())
				.bankName(bank.getBankName())
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	public static class OrderId {
		private final String orderId;
	}
}

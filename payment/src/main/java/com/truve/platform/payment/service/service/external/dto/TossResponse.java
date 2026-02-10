package com.truve.platform.payment.service.service.external.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.Getter;
import lombok.NoArgsConstructor;

public class TossResponse {

	@Getter
	@NoArgsConstructor
	public static class Payment {
		private String paymentKey;
		private String orderId;
		private Long totalAmount;
		private String status;
		private String approvedAt;

		private Receipt receipt;
		private Card card;
		private EasyPay easyPay;

		@JsonAnySetter
		private Map<String, Object> others = new HashMap<>();
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
	}

	@Getter
	@NoArgsConstructor
	public static class EasyPay {
		private String provider;
		private Long amount;
	}

	@Getter
	@NoArgsConstructor
	public static class Error {
		private String version;
		private String traceId;
		private ErrorDetail error;
	}

	@Getter
	@NoArgsConstructor
	public static class ErrorDetail {
		private String code;
		private String message;
	}

}
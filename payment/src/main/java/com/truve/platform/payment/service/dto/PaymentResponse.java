package com.truve.platform.payment.service.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.domain.entity.Payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class PaymentResponse {

	@Getter
	@AllArgsConstructor
	public static class Create {
		Long paymentId;
	}

	@Getter
	@Builder
	public static class Details {
		// TODO: 피그마 확인 후 전달 정보 검토 필요
		private final String orderId;
		private final String paymentKey;
		private final Long amount;
		private final String method;
		private final PaymentStatus status;
		private final Long cancelableAmount;
		private final VirtualAccountDetails virtualAccount;
		private final CardDetails card;
		private final EasyPayDetails easyPay;
		private final String failReason;
		private final LocalDateTime requestedAt;
		private final LocalDateTime approvedAt;

		public static Details from(Payment payment) {
			return Details.builder()
				.orderId(payment.getOrderId())
				.paymentKey(payment.getPaymentKey())
				.amount(payment.getAmount())
				.method(payment.getMethod().getDisplayName())
				.status(payment.getStatus())
				.cancelableAmount(payment.getCancelableAmount())
				.virtualAccount(
					payment.getVirtualAccount() == null ? null : VirtualAccountDetails.from(payment.getVirtualAccount()))
				.card(payment.getCard() == null ? null : CardDetails.from(payment.getCard()))
				.easyPay(payment.getEasyPay() == null ? null : EasyPayDetails.from(payment.getEasyPay()))
				.failReason(payment.getFailReason())
				.requestedAt(payment.getRequestedAt())
				.approvedAt(payment.getApprovedAt())
				.build();
		}
	}

	@Getter
	@Builder
	private static class CardDetails {
		private final String cardCompanyName;
		private final String number;
		private final Integer installmentPlanMonths;

		public static CardDetails from(com.truve.platform.payment.service.domain.entity.Card card) {
			return CardDetails.builder()
				.cardCompanyName(card.getIssuer().getCardCompanyName())
				.number(card.getNumber())
				.installmentPlanMonths(card.getInstallmentPlanMonths())
				.build();
		}
	}

	@Getter
	@Builder
	private static class EasyPayDetails {
		private final String provider;
		private final Long discountAmount;

		public static EasyPayDetails from(com.truve.platform.payment.service.domain.entity.EasyPay easyPay) {
			return EasyPayDetails.builder()
				.provider(easyPay.getProvider())
				.discountAmount(easyPay.getDiscountAmount())
				.build();
		}
	}

	@Getter
	@Builder
	private static class VirtualAccountDetails {
		private final String accountNumber;
		private final String bankName;
		private final String customerName;
		private final LocalDateTime dueDate;
		private final String displayDueDate;
		private final String remainingTime;

		public static VirtualAccountDetails from(
			com.truve.platform.payment.service.domain.entity.VirtualAccount virtualAccount) {
			return VirtualAccountDetails.builder()
				.accountNumber(virtualAccount.getAccountNumber())
				.bankName(virtualAccount.getBank().getBankName())
				.customerName(virtualAccount.getCustomerName())
				.dueDate(virtualAccount.getDueDate())
				.displayDueDate(formatDueDate(virtualAccount.getDueDate()))
				.remainingTime(formatRemainingTime(virtualAccount.getDueDate()))
				.build();
		}

		private static String formatDueDate(LocalDateTime dateTime) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일(E) HH시 mm분까지", Locale.KOREAN);
			return dateTime.format(formatter);
		}

		private static String formatRemainingTime(LocalDateTime dateTime) {
			LocalDateTime now = LocalDateTime.now();

			// TODO: 만료시 형식 확인 필요
			if (now.isAfter(dateTime))
				return "입금 시간이 만료되었습니다.";

			Duration duration = Duration.between(now, dateTime);
			long days = duration.toDays();
			long hours = duration.toHoursPart();
			long minutes = duration.toMinutesPart();

			StringBuilder sb = new StringBuilder("입금 마감 ");
			if (days > 0)
				sb.append(days).append("일 ");
			if (hours > 0)
				sb.append(hours).append("시간 ");
			if (minutes > 0)
				sb.append(minutes).append("분 ");
			sb.append("전");

			return sb.toString();
		}
	}

}

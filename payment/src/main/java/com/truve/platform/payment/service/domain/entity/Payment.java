package com.truve.platform.payment.service.domain.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.payment.service.domain.constant.CancelType;
import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String orderId;

	@Column(unique = true)
	private String paymentKey;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false)
	private Long cancelableAmount;

	private String failReason;

	@OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
	private List<PaymentCancel> cancels = new ArrayList<>();

	private LocalDateTime approvedAt;

	@Builder
	public Payment(String orderId, Long amount, PaymentMethod method) {
		this.orderId = orderId;
		this.amount = amount;
		this.cancelableAmount = amount;
		this.method = method;
		this.status = PaymentStatus.READY;
	}

	public void expire() {
		validateExpireStatus();

		this.status = PaymentStatus.EXPIRED;
		this.cancelableAmount = 0L;
	}

	public void validateAmount(Long amount) {
		Preconditions.validate(this.amount.equals(amount), ErrorCode.INVALID_PAYMENT_AMOUNT);
	}

	public void processConfirm(String paymentKey, LocalDateTime approvedAt) {
		if (this.method == PaymentMethod.TRANSFER) {
			waitDeposit(paymentKey);
		} else {
			complete(paymentKey, approvedAt);
		}
	}

	public void waitDeposit(String paymentKey) {
		validateWaitDepositStatus();

		this.paymentKey = paymentKey;
		this.status = PaymentStatus.WAITING_FOR_DEPOSIT;
	}

	public void complete(String paymentKey, LocalDateTime approvedAt) {
		validateCompleteStatus();
		verifyPaymentKey(paymentKey);

		this.paymentKey = paymentKey;
		this.status = PaymentStatus.DONE;
		this.approvedAt = approvedAt;
	}

	public void applyCancel(Long cancelAmount, String reason, CancelType type) {
		validateCancelStatus();
		validateCancelPolicy(type);
		validateCancelAmount(cancelAmount, type);

		if (status.isCancelable()) {
			processCancel();
		} else if (status.isRefundable()) {
			processRefund(cancelAmount);
		}

		this.cancels.add(new PaymentCancel(this, cancelAmount, reason, type));
	}

	private void processCancel() {
		this.cancelableAmount = 0L;
		this.status = PaymentStatus.CANCELED;
	}

	private void processRefund(Long cancelAmount) {
		this.cancelableAmount -= cancelAmount;
		this.status = (this.cancelableAmount == 0) ? PaymentStatus.REFUNDED : PaymentStatus.PARTIAL_REFUNDED;
	}

	private void validateWaitDepositStatus() {
		Preconditions.validate(status == PaymentStatus.READY, ErrorCode.INVALID_PAYMENT_STATUS);
	}

	private void validateExpireStatus() {
		Preconditions.validate(status == PaymentStatus.WAITING_FOR_DEPOSIT, ErrorCode.INVALID_PAYMENT_STATUS);
	}

	private void validateCompleteStatus() {
		Preconditions.validate(status == PaymentStatus.READY || status == PaymentStatus.WAITING_FOR_DEPOSIT,
			ErrorCode.INVALID_PAYMENT_STATUS);
	}

	private void verifyPaymentKey(String paymentKey) {
		if (StringUtils.hasText(this.paymentKey)) {
			Preconditions.validate(this.paymentKey.equals(paymentKey), ErrorCode.INVALID_PAYMENT_KEY);
		}
	}

	private void validateCancelStatus() {
		Preconditions.validate(status.isCancelable() || status.isRefundable(), ErrorCode.INVALID_PAYMENT_STATUS);
	}

	private void validateCancelPolicy(CancelType type) {
		if (status.isCancelable()) {
			Preconditions.validate(type == CancelType.FULL, ErrorCode.MUST_CANCEL_FULL);
		}
	}

	private void validateCancelAmount(Long cancelAmount, CancelType type) {
		Preconditions.validate(cancelAmount > 0, ErrorCode.INVALID_CANCEL_AMOUNT);
		Preconditions.validate(cancelAmount <= cancelableAmount, ErrorCode.NOT_ENOUGH_CANCELABLE_AMOUNT);

		if (type == CancelType.FULL) {
			Preconditions.validate(cancelAmount.equals(amount), ErrorCode.INVALID_CANCEL_AMOUNT);
			Preconditions.validate(cancelAmount.equals(cancelableAmount), ErrorCode.INVALID_CANCEL_AMOUNT);
		}
	}
}

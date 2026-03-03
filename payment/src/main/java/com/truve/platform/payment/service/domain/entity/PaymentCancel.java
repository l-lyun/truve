package com.truve.platform.payment.service.domain.entity;

import java.time.LocalDateTime;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.payment.service.domain.command.CancelCommand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_cancels")
public class PaymentCancel extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@Column(nullable = false)
	private Long requestAmount;

	@Column(nullable = false)
	private Long refundFee;

	@Column(nullable = false)
	private Long refundAmount;

	@Column(nullable = false)
	private String cancelReason;

	@Column(nullable = false)
	private LocalDateTime canceledAt;

	@Column(unique = true, nullable = false)
	private String transactionKey;

	@Column(nullable = false)
	private String cancelStatus;

	@Builder
	public PaymentCancel(Payment payment, CancelCommand cancelCommand) {
		this.payment = payment;
		this.requestAmount = cancelCommand.getAmount();
		this.refundFee = cancelCommand.getFee();
		this.refundAmount = requestAmount - refundFee;
		this.cancelReason = cancelCommand.getReason();
		this.canceledAt = cancelCommand.getCanceledAt();
		this.transactionKey = cancelCommand.getTransactionKey();
		this.cancelStatus = cancelCommand.getStatus();
	}

}

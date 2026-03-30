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
	private Long canceledAmount;

	@Column(nullable = false)
	private String cancelReason;

	@Column(nullable = false)
	private LocalDateTime canceledAt;

	@Column(unique = true, nullable = false)
	private String idempotencyKey;

	@Column(nullable = false)
	private String cancelStatus;

	@Builder
	public PaymentCancel(Payment payment, CancelCommand cancelCommand) {
		this.payment = payment;
		this.canceledAmount = cancelCommand.getAmount();
		this.cancelReason = cancelCommand.getReason();
		this.canceledAt = cancelCommand.getCanceledAt();
		this.idempotencyKey = cancelCommand.getIdempotencyKey();
		this.cancelStatus = cancelCommand.getStatus();
	}

}

package com.truve.platform.payment.service.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.payment.service.domain.command.CancelCommand;
import com.truve.platform.payment.service.domain.constant.Bank;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.domain.entity.PaymentCancel;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.repository.PaymentCancelRepository;
import com.truve.platform.payment.service.repository.PaymentRepository;
import com.truve.platform.payment.service.service.external.TossClient;
import com.truve.platform.payment.service.service.external.dto.TossRequest;
import com.truve.platform.payment.service.service.external.dto.TossResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
	private static final List<PaymentResponse.Bank> CACHED_BANKS = Stream.of(Bank.values())
		.map(PaymentResponse.Bank::from)
		.toList();

	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final TossClient tossClient;

	@Transactional(readOnly = true)
	public PaymentResponse.Details details(String orderId) {
		Payment payment = paymentRepository.findByOrderIdOrThrow(orderId);
		return PaymentResponse.Details.from(payment);
	}

	@Transactional
	public Long create(PaymentRequest.Create request) {
		// TODO: 이미 존재하는 결제여도 READY 상태면 찾아서 ID 반환 (결제창을 닫아서 재요청하는 경우)
		Preconditions.validate(!paymentRepository.existsByOrderId(request.getOrderId()), ErrorCode.ALREADY_EXIST_PAYMENT);

		Payment payment = Payment.builder()
			.orderId(request.getOrderId())
			.amount(request.getAmount())
			.build();

		return paymentRepository.save(payment).getId();
	}

	public List<PaymentResponse.Bank> getBankList() {
		return CACHED_BANKS;
	}

	@Transactional
	public void confirm(String orderId, String paymentKey, Long amount) {
		Payment payment = paymentRepository.findByOrderIdOrThrow(orderId);

		Preconditions.validate(payment.isNotDone(), ErrorCode.ALREADY_DONE_PAYMENT);
		payment.validateAmount(amount);

		TossResponse.Payment response = tossClient.confirm(new TossRequest.Confirm(orderId, amount, paymentKey));

		payment.confirm(
			response.getPaymentKey(),
			response.getMethodDetailsEntity(),
			parseTime(response.getRequestedAt()),
			parseTime(response.getApprovedAt())
		);
	}

	@Transactional
	public void completeDeposit(String orderId, String approvedAt) {
		Payment payment = paymentRepository.findByOrderIdOrThrow(orderId);

		Preconditions.validate(payment.isNotDone(), ErrorCode.ALREADY_DONE_PAYMENT);

		payment.completeDeposit(parseTime(approvedAt));
	}

	@Transactional
	public PaymentResponse.Cancel cancel(String orderId, String idempotencyKey, PaymentRequest.Cancel request) {
		Payment payment = paymentRepository.findByOrderIdOrThrow(orderId);
		payment.validateCancel(request.getCancelAmount());

		Long refundFee = 0L; // TODO: 환불 수수료 계산 구현 후 추가

		TossResponse.Cancel response = tossClient.cancel(
			payment.getPaymentKey(),
			idempotencyKey,
			TossRequest.Cancel.from(request, refundFee));

		return paymentCancelRepository.findByTransactionKey(response.getTransactionKey())
			.map(PaymentResponse.Cancel::from)
			.orElseGet(() -> {
				PaymentCancel cancel = payment.applyCancel(toCancelCommand(response, 0L));
				return PaymentResponse.Cancel.from(cancel);
			});
	}

	private CancelCommand toCancelCommand(TossResponse.Cancel latestCancel, Long refundFee) {
		return CancelCommand.builder()
			.amount(latestCancel.getCancelAmount())
			.fee(refundFee)
			.reason(latestCancel.getCancelReason())
			.canceledAt(parseTime(latestCancel.getCanceledAt()))
			.transactionKey(latestCancel.getTransactionKey())
			.status(latestCancel.getCancelStatus())
			.build();
	}

	private LocalDateTime parseTime(String time) {
		return !StringUtils.hasText(time) ? null : LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
	}
}

package com.truve.platform.payment.service.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.payment.service.domain.command.CancelCommand;
import com.truve.platform.payment.service.domain.constant.Bank;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.domain.entity.PaymentCancel;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.event.PaymentUpdated;
import com.truve.platform.payment.service.external.client.TossClient;
import com.truve.platform.payment.service.external.client.TossRequest;
import com.truve.platform.payment.service.external.client.TossResponse;
import com.truve.platform.payment.service.external.kafka.PaymentEventCommand;
import com.truve.platform.payment.service.repository.PaymentCancelRepository;
import com.truve.platform.payment.service.repository.PaymentRepository;

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
	private final ApplicationEventPublisher eventPublisher;

	@Transactional(readOnly = true)
	public PaymentResponse.Details details(String orderId) {
		Payment payment = paymentRepository.getByOrderId(orderId);
		return PaymentResponse.Details.from(payment);
	}

	@Transactional
	public void create(PaymentEventCommand.Create request) {
		paymentRepository.findByOrderId(request.getOrderId())
			.ifPresentOrElse(
				this::handleExistingPayment,
				() -> saveNewPayment(request)
			);
	}

	private void handleExistingPayment(Payment p) {
		Preconditions.validate(p.getStatus() == PaymentStatus.READY, ErrorCode.ALREADY_EXIST_PAYMENT);
	}

	private void saveNewPayment(PaymentEventCommand.Create request) {
		Payment payment = Payment.builder()
			.orderId(request.getOrderId())
			.amount(request.getAmount())
			.build();
		paymentRepository.save(payment);
	}

	public List<PaymentResponse.Bank> getBankList() {
		return CACHED_BANKS;
	}

	@Transactional
	public PaymentResponse.OrderId confirm(PaymentRequest.Confirm request) {
		Payment payment = paymentRepository.getByOrderIdWithLock(request.getOrderId());

		Preconditions.validate(payment.isNotDone(), ErrorCode.ALREADY_DONE_PAYMENT);
		payment.validateAmount(request.getAmount());

		TossResponse.Payment response = tossClient.confirm(TossRequest.Confirm.of(request));

		payment.confirm(
			response.getPaymentKey(),
			response.getMethodDetailsEntity(),
			parseTime(response.getRequestedAt()),
			parseTime(response.getApprovedAt())
		);

		eventPublisher.publishEvent(PaymentUpdated.Confirmed.of(payment));

		return new PaymentResponse.OrderId(payment.getOrderId());
	}

	@Transactional
	public void completeDeposit(String orderId, String approvedAt) {
		Payment payment = paymentRepository.getByOrderIdWithLock(orderId);

		Preconditions.validate(payment.isNotDone(), ErrorCode.ALREADY_DONE_PAYMENT);

		payment.completeDeposit(parseTime(approvedAt));

		eventPublisher.publishEvent(PaymentUpdated.DepositReceived.of(payment));
	}

	@Transactional
	public PaymentResponse.Cancel cancel(String orderId, String idempotencyKey, PaymentRequest.Cancel request) {
		Payment payment = paymentRepository.getByOrderIdWithLock(orderId);
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

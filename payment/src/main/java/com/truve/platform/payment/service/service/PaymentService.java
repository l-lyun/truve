package com.truve.platform.payment.service.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.repository.PaymentRepository;
import com.truve.platform.payment.service.service.external.TossClient;
import com.truve.platform.payment.service.service.external.dto.TossRequest;
import com.truve.platform.payment.service.service.external.dto.TossResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
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

	@Transactional
	public void confirm(String orderId, String paymentKey, Long amount) {
		Payment payment = paymentRepository.findByOrderIdOrThrow(orderId);

		Preconditions.validate(!payment.isDone(), ErrorCode.ALREADY_DONE_PAYMENT);
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

		Preconditions.validate(!payment.isDone(), ErrorCode.ALREADY_DONE_PAYMENT);

		payment.completeDeposit(parseTime(approvedAt));
	}

	private LocalDateTime parseTime(String time) {
		return !StringUtils.hasText(time) ? null : LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
	}
}

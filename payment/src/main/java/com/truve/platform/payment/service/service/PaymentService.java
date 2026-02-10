package com.truve.platform.payment.service.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.dto.PaymentRequest;
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

	@Transactional
	public Long create(PaymentRequest.Create request) {
		Preconditions.validate(!paymentRepository.existsByOrderId(request.getOrderId()), ErrorCode.ALREADY_EXIST_PAYMENT);

		Payment payment = Payment.builder()
			.orderId(request.getOrderId())
			.amount(request.getAmount())
			.method(request.getMethod())
			.build();

		return paymentRepository.save(payment).getId();
	}

	@Transactional
	public void confirm(String orderId, String paymentKey, Long amount) {
		Payment payment = paymentRepository.findByOrderIdOrThrow(orderId);

		payment.validateAmount(amount);

		TossResponse.Payment response = tossClient.confirm(new TossRequest.Confirm(orderId, amount, paymentKey));

		payment.processConfirm(response.getPaymentKey(), parseLocalDateTime(response.getApprovedAt()));
	}

	private LocalDateTime parseLocalDateTime(String time) {
		return !StringUtils.hasText(time) ? null : OffsetDateTime.parse(time).toLocalDateTime();
	}
}

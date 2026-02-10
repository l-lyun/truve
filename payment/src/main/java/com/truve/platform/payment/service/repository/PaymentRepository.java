package com.truve.platform.payment.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.payment.service.domain.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(String orderId);

	boolean existsByOrderId(String orderId);

	default Payment findByOrderIdOrThrow(String orderId) {
		return findByOrderId(orderId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_PAYMENT)
		);
	}
}

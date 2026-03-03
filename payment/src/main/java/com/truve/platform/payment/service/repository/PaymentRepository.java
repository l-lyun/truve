package com.truve.platform.payment.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.payment.service.domain.entity.Payment;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(String orderId);

	boolean existsByOrderId(String orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.orderId = :orderId")
	Optional<Payment> findByOrderIdWithPessimisticLock(String orderId);

	default Payment findByOrderIdOrThrow(String orderId) {
		return findByOrderIdWithPessimisticLock(orderId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_PAYMENT)
		);
	}
}

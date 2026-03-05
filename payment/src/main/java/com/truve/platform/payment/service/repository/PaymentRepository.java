package com.truve.platform.payment.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.payment.service.domain.entity.Payment;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	boolean existsByOrderId(String orderId);

	@EntityGraph(attributePaths = {"cancels"})
	Optional<Payment> findByOrderId(String orderId);

	default Payment getByOrderId(String orderId) {
		return findByOrderId(orderId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_PAYMENT)
		);
	}

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.orderId = :orderId")
	@EntityGraph(attributePaths = {"cancels"})
	Optional<Payment> findByOrderIdWithLock(String orderId);

	default Payment getByOrderIdWithLock(String orderId) {
		return findByOrderIdWithLock(orderId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_PAYMENT)
		);
	}
}

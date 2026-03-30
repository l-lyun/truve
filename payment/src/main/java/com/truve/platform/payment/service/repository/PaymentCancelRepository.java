package com.truve.platform.payment.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.payment.service.domain.entity.PaymentCancel;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
	boolean existsByIdempotencyKey(String idempotencyKey);
}

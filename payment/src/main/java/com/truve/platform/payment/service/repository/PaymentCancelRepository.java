package com.truve.platform.payment.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.payment.service.domain.entity.PaymentCancel;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
	Optional<PaymentCancel> findByTransactionKey(String transactionKey);
}

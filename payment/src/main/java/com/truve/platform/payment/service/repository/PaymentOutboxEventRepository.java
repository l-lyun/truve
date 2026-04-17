package com.truve.platform.payment.service.repository;

import com.truve.platform.common.outbox.OutboxEventRepository;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;

public interface PaymentOutboxEventRepository extends OutboxEventRepository<PaymentOutboxEvent> {
}

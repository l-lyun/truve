package com.truve.platform.payment.service.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.common.outbox.OutboxEventRepository;
import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.payment.service.domain.entity.PaymentOutboxEvent;

public interface PaymentOutboxEventRepository extends OutboxEventRepository<PaymentOutboxEvent> {
	@Query("""
		select event
		from PaymentOutboxEvent event
		where event.status = :status
		  and not exists (
			select older.id
			from PaymentOutboxEvent older
			where older.topic = event.topic
			  and older.messageKey = event.messageKey
			  and older.id < event.id
			  and older.status in :activeStatuses
		  )
		order by event.retryCount asc, event.id asc
		""")
	List<PaymentOutboxEvent> findRelayHeads(
		@Param("status") OutboxStatus status,
		@Param("activeStatuses") List<OutboxStatus> activeStatuses,
		Pageable pageable
	);
}

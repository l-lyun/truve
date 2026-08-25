package org.truve.platform.ticketing.service.booking.outbox.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;

import com.truve.platform.common.outbox.OutboxEventRepository;
import com.truve.platform.common.outbox.OutboxStatus;

public interface TicketingOutboxEventRepository extends OutboxEventRepository<TicketingOutboxEvent> {
	@Query("""
		select event
		from TicketingOutboxEvent event
		where event.status = :status
		  and not exists (
			select older.id
			from TicketingOutboxEvent older
			where older.topic = event.topic
			  and older.messageKey = event.messageKey
			  and older.id < event.id
			  and older.status in :activeStatuses
		  )
		order by event.retryCount asc, event.id asc
		""")
	List<TicketingOutboxEvent> findRelayHeads(
		@Param("status") OutboxStatus status,
		@Param("activeStatuses") List<OutboxStatus> activeStatuses,
		Pageable pageable
	);
}

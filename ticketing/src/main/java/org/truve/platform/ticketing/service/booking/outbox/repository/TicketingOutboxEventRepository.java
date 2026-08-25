package org.truve.platform.ticketing.service.booking.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;

import com.truve.platform.common.outbox.OutboxEventRepository;
import com.truve.platform.common.outbox.OutboxStatus;

public interface TicketingOutboxEventRepository extends OutboxEventRepository<TicketingOutboxEvent> {
	@Query(value = """
		select event.*
		from ticketing_outbox_events event
		where event.status in ('PENDING', 'FAILED')
		  and not exists (
			select 1
			from ticketing_outbox_events older
			where older.topic = event.topic
			  and older.message_key = event.message_key
			  and older.id < event.id
			  and older.status in ('PENDING', 'FAILED', 'PROCESSING')
		  )
		order by case when event.status = 'PENDING' then 0 else 1 end,
		         event.retry_count asc,
		         event.id asc
		limit :batchSize
		for update skip locked
		""", nativeQuery = true)
	List<TicketingOutboxEvent> findClaimableHeadsForUpdate(
		@Param("batchSize") int batchSize
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update TicketingOutboxEvent event
		set event.status = :published,
		    event.claimToken = null,
		    event.claimedAt = null
		where event.id = :id
		  and event.status = :processing
		  and event.claimToken = :claimToken
		""")
	int markPublishedIfOwned(
		@Param("id") Long id,
		@Param("claimToken") UUID claimToken,
		@Param("processing") OutboxStatus processing,
		@Param("published") OutboxStatus published
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update TicketingOutboxEvent event
		set event.status = :failed,
		    event.retryCount = event.retryCount + 1,
		    event.claimToken = null,
		    event.claimedAt = null
		where event.id = :id
		  and event.status = :processing
		  and event.claimToken = :claimToken
		""")
	int markFailedIfOwned(
		@Param("id") Long id,
		@Param("claimToken") UUID claimToken,
		@Param("processing") OutboxStatus processing,
		@Param("failed") OutboxStatus failed
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update TicketingOutboxEvent event
		set event.status = :failed,
		    event.retryCount = event.retryCount + 1,
		    event.claimToken = null,
		    event.claimedAt = null
		where event.status = :processing
		  and event.claimedAt < :expiredBefore
		""")
	int recoverExpiredClaims(
		@Param("expiredBefore") LocalDateTime expiredBefore,
		@Param("processing") OutboxStatus processing,
		@Param("failed") OutboxStatus failed
	);
}

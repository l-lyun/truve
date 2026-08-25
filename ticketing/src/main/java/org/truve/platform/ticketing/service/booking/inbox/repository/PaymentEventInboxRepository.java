package org.truve.platform.ticketing.service.booking.inbox.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.booking.inbox.domain.entity.PaymentEventInbox;

public interface PaymentEventInboxRepository extends JpaRepository<PaymentEventInbox, Long> {
	boolean existsByEventId(UUID eventId);

	Optional<PaymentEventInbox> findByEventId(UUID eventId);
}

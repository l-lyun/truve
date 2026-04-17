package org.truve.platform.ticketing.service.booking.risk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.booking.risk.domain.entity.BookingBotRiskSummary;

public interface BookingBotRiskSummaryRepository extends JpaRepository<BookingBotRiskSummary, Long> {
	Optional<BookingBotRiskSummary> findByUserId(UUID userId);
}

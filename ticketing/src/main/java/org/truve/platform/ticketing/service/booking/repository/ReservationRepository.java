package org.truve.platform.ticketing.service.booking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;

import io.lettuce.core.dynamic.annotation.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
	@EntityGraph(attributePaths = {"tickets"})
	Reservation findByNumber(String number);

	@Query("""
		SELECT DISTINCT r
		FROM Reservation r
		JOIN FETCH r.tickets
		WHERE r.userId = :userId
		  AND (:from IS NULL OR r.bookedAt >= :from)
		  AND (:to IS NULL OR r.bookedAt <= :to)
		 	AND r.status IN ('PENDING_DEPOSIT', 'CONFIRMED', 'COMPLETED', 'PARTIAL_CANCELED', 'CANCELED')
		ORDER BY r.bookedAt DESC
		""")
	List<Reservation> findByUserIdAndDateRange(
		@Param("userId") UUID userId,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to
	);
}

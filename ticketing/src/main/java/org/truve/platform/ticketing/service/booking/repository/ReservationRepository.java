package org.truve.platform.ticketing.service.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}

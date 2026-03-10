package org.truve.platform.ticketing.service.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.ticket.domain.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}

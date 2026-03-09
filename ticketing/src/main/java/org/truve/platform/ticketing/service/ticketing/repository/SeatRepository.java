package org.truve.platform.ticketing.service.ticketing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}

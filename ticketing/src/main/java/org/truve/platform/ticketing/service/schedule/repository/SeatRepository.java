package org.truve.platform.ticketing.service.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.schedule.domain.entity.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}

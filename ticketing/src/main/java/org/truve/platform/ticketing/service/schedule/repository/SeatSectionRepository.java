package org.truve.platform.ticketing.service.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.schedule.domain.entity.SeatSection;

public interface SeatSectionRepository extends JpaRepository<SeatSection, Long> {
}

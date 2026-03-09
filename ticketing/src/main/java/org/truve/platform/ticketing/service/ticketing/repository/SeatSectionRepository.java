package org.truve.platform.ticketing.service.ticketing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.ticketing.domain.entity.SeatSection;

public interface SeatSectionRepository extends JpaRepository<SeatSection, Long> {
}

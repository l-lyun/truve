package org.truve.platform.ticketing.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truve.platform.ticketing.service.domain.entity.ShowScheduleSeat;

public interface ShowScheduleSeatRepository extends JpaRepository<ShowScheduleSeat, Long> {
}

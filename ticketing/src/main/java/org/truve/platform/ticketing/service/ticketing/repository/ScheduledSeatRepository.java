package org.truve.platform.ticketing.service.ticketing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto;

import org.springframework.data.repository.query.Param;

public interface ScheduledSeatRepository extends JpaRepository<ScheduledSeat, Long> {

	// TODO: N+1, 성능 개선 등 확인 필요
	@Query("""
	select SeatSectionsDto(
			sc.id,
 			sc.name,
 			sc.gradeName,
 			sc.price,
 			s.id,
 			s.seatRow,
 			s.seatNumber,
 			ss.status
	)
	from ScheduledSeat ss
	join ss.seat s
	join s.seatSection sc
	where ss.showScheduleId = :showScheduleId
	order by sc.id asc, s.seatRow asc, s.seatNumber asc
""")
	List<SeatSectionsDto> findSeatSectionByScheduledSeatId(@Param("showScheduleId") Long showScheduleId);

}

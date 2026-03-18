package org.truve.platform.ticketing.service.ticketing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truve.platform.ticketing.service.booking.external.client.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto;

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

	@Query("""
		SELECT 
			shs.showId AS showId,
			shs.title AS showTitle,
			shs.venueName AS venueName,
			shs.startAt AS startAt,
			shs.posterImg AS posterImg,
			sc.name AS sectionName,
			sc.floor AS floor,
			sc.gradeName AS gradeName,
			s.seatRow AS seatRow,
			s.seatNumber AS seatNumber,
			sc.price AS price
		FROM ScheduledSeat ss 
		JOIN ss.seat s 
		JOIN s.seatSection sc
		JOIN ShowScheduled shs ON ss.showScheduleId = shs.id 
		WHERE s.id IN :ids
		""")
	List<TicketingResponse.FlatSeatInfo> findFlatInfoById(List<Long> ids);
}

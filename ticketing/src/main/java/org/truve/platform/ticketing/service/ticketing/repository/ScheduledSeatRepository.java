package org.truve.platform.ticketing.service.ticketing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truve.platform.ticketing.service.booking.external.client.ticketing.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingInternalResponse;

import jakarta.persistence.LockModeType;

public interface ScheduledSeatRepository extends JpaRepository<ScheduledSeat, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select ss
		from ScheduledSeat ss
		join fetch ss.seat s
		join fetch s.seatSection sc
		where ss.id in :ids
		order by ss.id asc
		""")
	List<ScheduledSeat> findAllByIdForUpdate(@Param("ids") List<Long> ids);

	// TODO: N+1, 성능 개선 등 확인 필요
	@Query("""
	select new org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto(
			sc.id,
 			sc.name,
 			sc.gradeName,
 			sc.price,
 			ss.id,
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
				ss.showScheduleId AS showScheduleId,
				shs.title AS showTitle,
				shs.venueName AS venueName,
				shs.startAt AS startAt,
				shs.posterImg AS posterImg,
				ss.id AS scheduledSeatId,
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
		WHERE ss.id IN :ids
		""")
	List<TicketingResponse.FlatSeatInfo> findFlatInfoById(List<Long> ids);

	/*
	[백엔드 내부 통신 용도 Repository 메서드]
	*/

	@Query("""
	select
		sc.gradeName as gradeName,
		sum(case when ss.status = :status then 1L else 0L end) as remainingSeatCount,
		count(ss) as totalCount
	from ScheduledSeat ss
	join ss.seat s
	join s.seatSection sc
	where ss.showScheduleId = :showScheduleId
	group by sc.gradeName
	order by sc.gradeName asc
	""")
	List<TicketingInternalResponse.FlatRemainingSeatInfo> findGradeRemainingSeats(
		@Param("showScheduleId") Long showScheduleId,
		@Param("status") SeatStatus status
	);


}

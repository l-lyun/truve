package com.truve.platform.musical.show.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.ShowSchedule;

public interface ShowScheduleRepository extends JpaRepository<ShowSchedule, Long> {

	@Query("""
		select s
		from ShowSchedule s
		where s.show.id = :showId
		order by s.showTime asc
		""")
	List<ShowSchedule> findSchedules(@Param("showId") Long showId);

	@Query("""
		select s
		from ShowSchedule s
		where s.show.id = :showId
		and (:fromTime is null or s.showTime >= :fromTime)
		and (:toTime is null or s.showTime <= :toTime)
		and (
			:artistFilterOff = true
			or exists (
				select 1
				from ShowScheduleCasting sc
				where sc.showSchedule = s
				and sc.showCasting.artist.id in :artistIds
			)
		)
		""")
	Page<ShowSchedule> findCastingSchedules(
		@Param("showId") Long showId,
		@Param("fromTime") LocalDateTime fromTime,
		@Param("toTime") LocalDateTime toTime,
		@Param("artistFilterOff") boolean artistFilterOff,
		@Param("artistIds") List<Long> artistIds,
		Pageable pageable
	);
}

package com.truve.platform.musical.show.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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
}

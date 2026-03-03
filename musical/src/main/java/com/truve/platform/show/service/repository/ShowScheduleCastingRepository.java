package com.truve.platform.show.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.show.service.domain.entity.ShowScheduleCasting;

public interface ShowScheduleCastingRepository extends JpaRepository<ShowScheduleCasting, Long> {

	@Query("""
		select sc
		from ShowScheduleCasting sc
		join fetch sc.showSchedule s
		join fetch sc.showCasting c
		join fetch c.artist
		where s.id in :scheduleIds
		""")
	List<ShowScheduleCasting> findAllByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);
}

package com.truve.platform.show.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.show.service.domain.entity.ShowSeatGrade;

public interface ShowSeatGradeRepository extends JpaRepository<ShowSeatGrade, Long> {

	@Query("""
		select p
		from ShowSeatGrade p
		where p.show.id = :showId
		order by p.basePrice desc
		""")
	List<ShowSeatGrade> findSeatPrices(@Param("showId") Long showId);
}


package com.truve.platform.musical.show.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.ShowSectionGrade;

public interface ShowSeatGradeRepository extends JpaRepository<ShowSectionGrade, Long> {

	@Query("""
		select p
		from ShowSectionGrade p
		where p.show.id = :showId
		order by p.id asc
		""")
	List<ShowSectionGrade> findSeatPrices(@Param("showId") Long showId);
}

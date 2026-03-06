package com.truve.platform.musical.show.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.ShowCasting;

public interface ShowCastingRepository extends JpaRepository<ShowCasting, Long> {

	@Query("""
		select c
		from ShowCasting c
		join fetch c.artist
		where c.show.id = :showId
		order by
			case when c.castingOrder is null then 1 else 0 end asc,
			c.castingOrder asc,
			c.id asc
		""")
	List<ShowCasting> findAllByShowId(@Param("showId") Long showId);
}

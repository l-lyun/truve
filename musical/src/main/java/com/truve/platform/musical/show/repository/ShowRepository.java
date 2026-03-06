package com.truve.platform.musical.show.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.show.domain.entity.Show;

public interface ShowRepository extends JpaRepository<Show, Long> {

	@Query("""
		select p
		from Show p
		where p.id = :showId
		""")
	java.util.Optional<Show> findDetailById(@Param("showId") Long showId);

	default Show findByIdOrThrow(Long showId) {
		return findDetailById(showId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_SHOW)
		);
	}
}

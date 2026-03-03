package com.truve.platform.show.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.show.service.domain.entity.Show;

public interface ShowRepository extends JpaRepository<Show, Long> {

	@Query("""
		select p
		from Show p
		join fetch p.venue
		where p.id = :showId
		""")
	java.util.Optional<Show> findDetailById(@Param("showId") Long showId);

	default Show findByIdOrThrow(Long showId) {
		return findDetailById(showId).orElseThrow(
			() -> new CustomException(ErrorCode.NOT_FOUND_SHOW)
		);
	}
}

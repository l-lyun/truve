package com.truve.platform.musical.show.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.show.domain.entity.Show;

public interface ShowRepository extends JpaRepository<Show, Long> {

	@Query("""
		select s
		from Show s
		left join ShowStats st on st.showId = s.id
		where (
			s.endTime is null or s.endTime >= :now
		)
		and (
			:regionKeyword is null
			or exists (
				select 1
				from Venue v
				where v.id = s.venueId
				and v.address like concat('%', :regionKeyword, '%')
			)
		)
		order by
			case when st.dailyRank is null then 1 else 0 end asc,
			st.dailyRank desc,
			s.id desc
		""")
	Page<Show> findHomeShowsOrderByDailyRank(
		@Param("regionKeyword") String regionKeyword,
		@Param("now") LocalDateTime now,
		Pageable pageable
	);

	@Query("""
		select s
		from Show s
		left join ShowStats st on st.showId = s.id
		where (
			s.endTime is null or s.endTime >= :now
		)
		and (
			:regionKeyword is null
			or exists (
				select 1
				from Venue v
				where v.id = s.venueId
				and v.address like concat('%', :regionKeyword, '%')
			)
		)
		order by
			case when st.weeklyRank is null then 1 else 0 end asc,
			st.weeklyRank desc,
			s.id desc
		""")
	Page<Show> findHomeShowsOrderByWeeklyRank(
		@Param("regionKeyword") String regionKeyword,
		@Param("now") LocalDateTime now,
		Pageable pageable
	);

	@Query("""
		select s
		from Show s
		where (
			s.endTime is null or s.endTime >= :now
		)
		and (
			:regionKeyword is null
			or exists (
				select 1
				from Venue v
				where v.id = s.venueId
				and v.address like concat('%', :regionKeyword, '%')
			)
		)
		order by
			case when s.endTime is null then 1 else 0 end asc,
			s.endTime asc,
			s.id asc
		""")
	Page<Show> findHomeShowsOrderByEndingSoon(
		@Param("regionKeyword") String regionKeyword,
		@Param("now") LocalDateTime now,
		Pageable pageable
	);

	@Query("""
		select s
		from Show s
		left join ShowStats st on st.showId = s.id
		where (
			s.endTime is null or s.endTime >= :now
		)
		and (
			:regionKeyword is null
			or exists (
				select 1
				from Venue v
				where v.id = s.venueId
				and v.address like concat('%', :regionKeyword, '%')
			)
		)
		order by
			case when st.reviewCount is null then 1 else 0 end asc,
			st.reviewCount desc,
			s.id desc
		""")
	Page<Show> findHomeShowsOrderByReviewCount(
		@Param("regionKeyword") String regionKeyword,
		@Param("now") LocalDateTime now,
		Pageable pageable
	);

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

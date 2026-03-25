package com.truve.platform.musical.show.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.ShowCasting;

public interface ShowCastingRepository extends JpaRepository<ShowCasting, Long> {
    interface ArtistAppearanceProjection {
        Long getArtistId();

        Long getShowId();

        String getShowTitle();

        String getPosterImg();

        String getVenueName();

        LocalDateTime getStartTime();

        LocalDateTime getEndTime();
    }

    interface ArtistShowSummaryProjection {
        Long getShowId();

        String getShowTitle();

        String getPosterImg();

        String getVenueName();

        LocalDateTime getStartTime();

        LocalDateTime getEndTime();
    }

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

	@Query("""
		select distinct
			sc.artist.id as artistId,
			sc.show.id as showId,
			sc.show.title as showTitle,
			sc.show.posterImg as posterImg,
			v.name as venueName,
			sc.show.startTime as startTime,
			sc.show.endTime as endTime
		from ShowCasting sc
		left join Venue v on v.id = sc.show.venueId
		where sc.artist.id in :artistIds
		order by
			sc.artist.id asc,
			case
				when sc.show.startTime is null then 1
				else 0
			end asc,
			sc.show.startTime desc,
			sc.show.id desc
		""")
	List<ArtistAppearanceProjection> findAppearanceInfoByArtistIds(@Param("artistIds") List<Long> artistIds);

	@Query(
		value = """
		select distinct
			sc.show.id as showId,
			sc.show.title as showTitle,
			sc.show.posterImg as posterImg,
			v.name as venueName,
			sc.show.startTime as startTime,
			sc.show.endTime as endTime
		from ShowCasting sc
		left join Venue v on v.id = sc.show.venueId
		where sc.artist.id = :artistId
		and (sc.show.endTime is null or sc.show.endTime >= :now)
		order by
			case when sc.show.startTime is null then 1 else 0 end asc,
			sc.show.startTime asc,
			sc.show.id asc
		""")
	List<ArtistShowSummaryProjection> findCurrentShowsByArtistId(
		@Param("artistId") Long artistId,
		@Param("now") LocalDateTime now
	);

	@Query(
		value = """
		select distinct
			sc.show.id as showId,
			sc.show.title as showTitle,
			sc.show.posterImg as posterImg,
			v.name as venueName,
			sc.show.startTime as startTime,
			sc.show.endTime as endTime
		from ShowCasting sc
		left join Venue v on v.id = sc.show.venueId
		where sc.artist.id = :artistId
		and sc.show.endTime < :now
		order by
			sc.show.endTime desc,
			sc.show.id desc
		""",
		countQuery = """
		select count(distinct sc.show.id)
		from ShowCasting sc
		where sc.artist.id = :artistId
		and sc.show.endTime < :now
		"""
	)
	Page<ArtistShowSummaryProjection> findPastShowsByArtistId(
		@Param("artistId") Long artistId,
		@Param("now") LocalDateTime now,
		Pageable pageable
	);
}
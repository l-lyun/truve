package com.truve.platform.musical.show.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
	interface ArtistDetailProjection {
		Long getArtistId();

		String getArtistName();

		String getProfileImg();
	}

	interface ArtistSearchProjection {
		Long getArtistId();

		String getArtistName();

		String getProfileImg();
	}

	@Query("""
		select
			a.id as artistId,
			a.name as artistName,
			a.profileImg as profileImg
		from Artist a
		where a.name like concat('%', :keyword, '%')
		order by
			case
				when lower(a.name) = lower(:keyword) then 0
				else 1
			end asc,
			a.name asc,
			a.id asc
			""")
	List<ArtistSearchProjection> searchArtists(@Param("keyword") String keyword);

	@Query("""
		select
			a.id as artistId,
			a.name as artistName,
			a.profileImg as profileImg
		from Artist a
		where a.id = :artistId
		""")
	Optional<ArtistDetailProjection> findDetailById(@Param("artistId") Long artistId);

}
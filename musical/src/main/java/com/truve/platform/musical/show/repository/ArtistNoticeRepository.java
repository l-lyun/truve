package com.truve.platform.musical.show.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.ArtistNotice;

public interface ArtistNoticeRepository extends JpaRepository<ArtistNotice, Long> {

	interface ArtistNoticeProjection {
		Long getNoticeId();

		String getContent();
	}

	@Query("""
		select
			an.id as noticeId,
			an.content as content
		from ArtistNotice an
		where an.artist.id = :artistId
		order by
			case when an.displayOrder is null then 1 else 0 end asc,
			an.displayOrder asc,
			an.id asc
		""")
	List<ArtistNoticeProjection> findNoticesByArtistId(@Param("artistId") Long artistId);
}
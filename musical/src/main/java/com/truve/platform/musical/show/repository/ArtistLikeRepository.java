package com.truve.platform.musical.show.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.entity.ArtistLike;

public interface ArtistLikeRepository extends JpaRepository<ArtistLike, Long> {

	@Query("""
		select al.artist.id
		from ArtistLike al
		where al.userId = :userId
		and al.artist.id in :artistIds
		""")
	List<Long> findLikedArtistIds(
		@Param("userId") UUID userId,
		@Param("artistIds") Collection<Long> artistIds
	);

	boolean existsByUserIdAndArtistId(UUID userId, Long artistId);

	void deleteByUserIdAndArtistId(UUID userId, Long artistId);
}

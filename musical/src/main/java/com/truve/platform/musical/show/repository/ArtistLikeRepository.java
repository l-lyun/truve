package com.truve.platform.musical.show.repository;

import java.util.Collection;
import java.util.List;

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
		@Param("userId") Long userId,
		@Param("artistIds") Collection<Long> artistIds
	);

	boolean existsByUserIdAndArtistId(Long userId, Long artistId);

	void deleteByUserIdAndArtistId(Long userId, Long artistId);
}

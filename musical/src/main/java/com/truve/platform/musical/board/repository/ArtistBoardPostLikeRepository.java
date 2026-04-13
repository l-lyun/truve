package com.truve.platform.musical.board.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.board.domain.entity.ArtistBoardPostLike;

public interface ArtistBoardPostLikeRepository extends JpaRepository<ArtistBoardPostLike, Long> {

	interface PostLikeCountProjection {
		Long getPostId();

		long getLikeCount();
	}

	@Query("""
		select
			pl.post.id as postId,
			count(pl.id) as likeCount
		from ArtistBoardPostLike pl
		where pl.post.id in :postIds
		group by pl.post.id
		""")
	List<PostLikeCountProjection> countLikesByPostIds(@Param("postIds") Collection<Long> postIds);

	@Query("""
		select pl.post.id
		from ArtistBoardPostLike pl
		where pl.userId = :userId
		and pl.post.id in :postIds
		""")
	List<Long> findLikedPostIds(
		@Param("userId") UUID userId,
		@Param("postIds") Collection<Long> postIds
	);

	boolean existsByUserIdAndPostId(UUID userId, Long postId);

	void deleteByUserIdAndPostId(UUID userId, Long postId);
}

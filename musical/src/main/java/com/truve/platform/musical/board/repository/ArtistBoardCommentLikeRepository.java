package com.truve.platform.musical.board.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.board.domain.entity.ArtistBoardCommentLike;

public interface ArtistBoardCommentLikeRepository extends JpaRepository<ArtistBoardCommentLike, Long> {

	interface CommentLikeCountProjection {
		Long getCommentId();

		long getLikeCount();
	}

	@Query("""
		select
			cl.comment.id as commentId,
			count(cl.id) as likeCount
		from ArtistBoardCommentLike cl
		where cl.comment.id in :commentIds
		group by cl.comment.id
		""")
	List<CommentLikeCountProjection> countLikesByCommentIds(@Param("commentIds") Collection<Long> commentIds);

	@Query("""
		select cl.comment.id
		from ArtistBoardCommentLike cl
		where cl.userId = :userId
		and cl.comment.id in :commentIds
		""")
	List<Long> findLikedCommentIds(
		@Param("userId") UUID userId,
		@Param("commentIds") Collection<Long> commentIds
	);

	boolean existsByUserIdAndCommentId(UUID userId, Long commentId);

	void deleteByUserIdAndCommentId(UUID userId, Long commentId);
}

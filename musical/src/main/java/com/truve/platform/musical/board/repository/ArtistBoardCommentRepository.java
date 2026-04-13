package com.truve.platform.musical.board.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentAuthorType;
import com.truve.platform.musical.board.domain.entity.ArtistBoardComment;

public interface ArtistBoardCommentRepository extends JpaRepository<ArtistBoardComment, Long> {

	interface PostCommentCountProjection {
		Long getPostId();

		long getCommentCount();
	}

	interface ReplyCountProjection {
		Long getParentCommentId();

		long getReplyCount();
	}

	@Query("""
		select
			c.post.id as postId,
			count(c.id) as commentCount
		from ArtistBoardComment c
		where c.post.id in :postIds
		group by c.post.id
		""")
	List<PostCommentCountProjection> countCommentsByPostIds(@Param("postIds") Collection<Long> postIds);

	@Query("""
		select
			c.parentComment.id as parentCommentId,
			count(c.id) as replyCount
		from ArtistBoardComment c
		where c.parentComment.id in :parentCommentIds
		group by c.parentComment.id
		""")
	List<ReplyCountProjection> countRepliesByParentCommentIds(@Param("parentCommentIds") Collection<Long> parentCommentIds);

	@EntityGraph(attributePaths = {"post", "post.artist", "parentComment"})
	List<ArtistBoardComment> findByPostIdAndParentCommentIsNullOrderByCreatedAtDescIdDesc(Long postId);

	@EntityGraph(attributePaths = {"post", "post.artist", "parentComment"})
	List<ArtistBoardComment> findByPostIdAndParentCommentIsNullAndUserIdOrderByCreatedAtDescIdDesc(Long postId, UUID userId);

	@EntityGraph(attributePaths = {"post", "post.artist", "parentComment"})
	List<ArtistBoardComment> findByPostIdAndParentCommentIsNullAndAuthorTypeOrderByCreatedAtDescIdDesc(
		Long postId,
		ArtistBoardCommentAuthorType authorType
	);

	@EntityGraph(attributePaths = {"post", "post.artist", "parentComment"})
	List<ArtistBoardComment> findByParentCommentIdOrderByCreatedAtDescIdDesc(Long parentCommentId);

	@EntityGraph(attributePaths = {"post", "post.artist", "parentComment"})
	Optional<ArtistBoardComment> findByIdAndPostId(Long commentId, Long postId);

	long countByPostId(Long postId);

	long countByPostIdAndParentCommentIsNull(Long postId);

	long countByPostIdAndParentCommentIsNullAndUserId(Long postId, UUID userId);

	long countByPostIdAndParentCommentIsNullAndAuthorType(Long postId, ArtistBoardCommentAuthorType authorType);
}

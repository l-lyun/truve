package com.truve.platform.musical.board.domain.entity;

import java.util.UUID;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentAuthorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "artist_board_comments")
public class ArtistBoardComment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private ArtistBoardPost post;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_comment_id")
	private ArtistBoardComment parentComment;

	@Enumerated(EnumType.STRING)
	@Column(name = "author_type", nullable = false)
	private ArtistBoardCommentAuthorType authorType;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "artist_id")
	private Long artistId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Builder
	private ArtistBoardComment(
		ArtistBoardPost post,
		ArtistBoardComment parentComment,
		ArtistBoardCommentAuthorType authorType,
		UUID userId,
		Long artistId,
		String content
	) {
		this.post = post;
		this.parentComment = parentComment;
		this.authorType = authorType;
		this.userId = userId;
		this.artistId = artistId;
		this.content = content;
	}
}

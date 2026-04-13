package com.truve.platform.musical.board.domain.entity;

import java.util.UUID;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "artist_board_post_likes",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_artist_board_post_likes_user_post",
			columnNames = {"user_id", "post_id"}
		)
	}
)
public class ArtistBoardPostLike extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private ArtistBoardPost post;

	@Builder
	private ArtistBoardPostLike(UUID userId, ArtistBoardPost post) {
		this.userId = userId;
		this.post = post;
	}
}

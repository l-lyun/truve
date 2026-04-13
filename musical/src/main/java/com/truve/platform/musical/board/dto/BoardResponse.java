package com.truve.platform.musical.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class BoardResponse {

	@Getter
	@AllArgsConstructor
	@Builder
	public static class PostFeed {
		private List<PostItem> posts;

		public static PostFeed of(List<PostItem> posts) {
			return PostFeed.builder()
				.posts(posts)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class PostItem {
		private Long postId;
		private LocalDateTime createdAt;
		private String artistName;
		private String artistThumbnailUrl;
		private String content;
		private List<String> imageUrls;
		private long likeCount;
		private long commentCount;
		private boolean likedByMe;

		public static PostItem of(
			Long postId,
			LocalDateTime createdAt,
			String artistName,
			String artistThumbnailUrl,
			String content,
			List<String> imageUrls,
			long likeCount,
			long commentCount,
			boolean likedByMe
		) {
			return PostItem.builder()
				.postId(postId)
				.createdAt(createdAt)
				.artistName(artistName)
				.artistThumbnailUrl(artistThumbnailUrl)
				.content(content)
				.imageUrls(imageUrls)
				.likeCount(likeCount)
				.commentCount(commentCount)
				.likedByMe(likedByMe)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class CommentList {
		private CommentSummary summary;
		private List<CommentItem> comments;

		public static CommentList of(CommentSummary summary, List<CommentItem> comments) {
			return CommentList.builder()
				.summary(summary)
				.comments(comments)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ReplyList {
		private List<CommentItem> replies;

		public static ReplyList of(List<CommentItem> replies) {
			return ReplyList.builder()
				.replies(replies)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class CommentSummary {
		private long totalCount;
		private long myCount;
		private long artistCount;

		public static CommentSummary of(long totalCount, long myCount, long artistCount) {
			return CommentSummary.builder()
				.totalCount(totalCount)
				.myCount(myCount)
				.artistCount(artistCount)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class CommentItem {
		private Long commentId;
		private LocalDateTime createdAt;
		private String authorName;
		private String authorThumbnailUrl;
		private String content;
		private long likeCount;
		private boolean likedByMe;
		private long replyCount;
		@JsonProperty("isMine")
		private boolean mine;
		@JsonProperty("isArtist")
		private boolean artist;

		public static CommentItem of(
			Long commentId,
			LocalDateTime createdAt,
			String authorName,
			String authorThumbnailUrl,
			String content,
			long likeCount,
			boolean likedByMe,
			long replyCount,
			boolean isMine,
			boolean isArtist
		) {
			return CommentItem.builder()
				.commentId(commentId)
				.createdAt(createdAt)
				.authorName(authorName)
				.authorThumbnailUrl(authorThumbnailUrl)
				.content(content)
				.likeCount(likeCount)
				.likedByMe(likedByMe)
				.replyCount(replyCount)
				.mine(isMine)
				.artist(isArtist)
				.build();
		}
	}
}

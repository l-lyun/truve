package com.truve.platform.musical.board.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentAuthorType;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentFilter;
import com.truve.platform.musical.board.domain.entity.ArtistBoardComment;
import com.truve.platform.musical.board.domain.entity.ArtistBoardCommentLike;
import com.truve.platform.musical.board.domain.entity.ArtistBoardPost;
import com.truve.platform.musical.board.domain.entity.ArtistBoardPostLike;
import com.truve.platform.musical.board.dto.BoardRequest;
import com.truve.platform.musical.board.dto.BoardResponse;
import com.truve.platform.musical.board.repository.ArtistBoardCommentRepository;
import com.truve.platform.musical.board.repository.ArtistBoardCommentLikeRepository;
import com.truve.platform.musical.board.repository.ArtistBoardPostLikeRepository;
import com.truve.platform.musical.board.repository.ArtistBoardPostRepository;
import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.dto.ArtistResponse;
import com.truve.platform.musical.show.service.ArtistService;
import com.truve.platform.musical.user.domain.entity.User;
import com.truve.platform.musical.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtistBoardService {

	private static final int MAX_IMAGE_COUNT = 4;
	private static final String UNKNOWN_MEMBER_NAME = "알 수 없는 사용자";

	private final ArtistBoardPostRepository artistBoardPostRepository;
	private final ArtistBoardPostLikeRepository artistBoardPostLikeRepository;
	private final ArtistBoardCommentRepository artistBoardCommentRepository;
	private final ArtistBoardCommentLikeRepository artistBoardCommentLikeRepository;
	private final ArtistService artistService;
	private final S3Service s3Service;
	private final UserRepository userRepository;
	private final ArtistRepository artistRepository;

	@Transactional(readOnly = true)
	public BoardResponse.PostFeed getPosts(Long artistId, UUID userId) {
		validateBoardAccessible(artistId, userId);

		List<ArtistBoardPost> posts = artistBoardPostRepository.findByArtistIdOrderByCreatedAtDescIdDesc(artistId);
		if (posts.isEmpty()) {
			return BoardResponse.PostFeed.of(List.of());
		}

		List<Long> postIds = posts.stream()
			.map(ArtistBoardPost::getId)
			.toList();

		Map<Long, Long> likeCounts = artistBoardPostLikeRepository.countLikesByPostIds(postIds).stream()
			.collect(Collectors.toMap(
				ArtistBoardPostLikeRepository.PostLikeCountProjection::getPostId,
				ArtistBoardPostLikeRepository.PostLikeCountProjection::getLikeCount
			));
		Map<Long, Long> commentCounts = artistBoardCommentRepository.countCommentsByPostIds(postIds).stream()
			.collect(Collectors.toMap(
				ArtistBoardCommentRepository.PostCommentCountProjection::getPostId,
				ArtistBoardCommentRepository.PostCommentCountProjection::getCommentCount
			));
		Set<Long> likedPostIds = artistBoardPostLikeRepository.findLikedPostIds(userId, postIds).stream()
			.collect(Collectors.toSet());

		List<BoardResponse.PostItem> items = posts.stream()
			.map(post -> toPostItem(post, likeCounts, commentCounts, likedPostIds))
			.toList();

		return BoardResponse.PostFeed.of(items);
	}

	@Transactional(readOnly = true)
	public BoardResponse.CommentList getComments(Long artistId, Long postId, UUID userId, ArtistBoardCommentFilter filter) {
		validateBoardAccessible(artistId, userId);
		ArtistBoardPost post = getPost(artistId, postId);

		List<ArtistBoardComment> comments = getRootCommentsByFilter(postId, userId, filter);
		Map<UUID, User> usersByUserId = findUsersByUserId(comments);
		Map<Long, Artist> artistsByArtistId = findArtistsByArtistId(comments);
		Map<Long, Long> likeCounts = findCommentLikeCounts(comments);
		Set<Long> likedCommentIds = findLikedCommentIds(comments, userId);
		Map<Long, Long> replyCounts = findReplyCounts(comments);

		List<BoardResponse.CommentItem> items = comments.stream()
			.map(comment -> toCommentItem(comment, userId, usersByUserId, artistsByArtistId, likeCounts, likedCommentIds, replyCounts))
			.toList();

		return BoardResponse.CommentList.of(
			BoardResponse.CommentSummary.of(
				artistBoardCommentRepository.countByPostIdAndParentCommentIsNull(post.getId()),
				artistBoardCommentRepository.countByPostIdAndParentCommentIsNullAndUserId(post.getId(), userId),
				artistBoardCommentRepository.countByPostIdAndParentCommentIsNullAndAuthorType(post.getId(), ArtistBoardCommentAuthorType.ARTIST)
			),
			items
		);
	}

	@Transactional(readOnly = true)
	public BoardResponse.ReplyList getReplies(Long artistId, Long postId, Long commentId, UUID userId) {
		validateBoardAccessible(artistId, userId);
		getPost(artistId, postId);
		ArtistBoardComment parentComment = getComment(postId, commentId);

		List<ArtistBoardComment> replies = artistBoardCommentRepository.findByParentCommentIdOrderByCreatedAtDescIdDesc(parentComment.getId());
		Map<UUID, User> usersByUserId = findUsersByUserId(replies);
		Map<Long, Artist> artistsByArtistId = findArtistsByArtistId(replies);
		Map<Long, Long> likeCounts = findCommentLikeCounts(replies);
		Set<Long> likedCommentIds = findLikedCommentIds(replies, userId);

		List<BoardResponse.CommentItem> items = replies.stream()
			.map(reply -> toCommentItem(reply, userId, usersByUserId, artistsByArtistId, likeCounts, likedCommentIds, Map.of()))
			.toList();

		return BoardResponse.ReplyList.of(items);
	}

	@Transactional
	public void createComment(Long artistId, Long postId, UUID userId, BoardRequest.CreateComment request) {
		validateBoardAccessible(artistId, userId);
		ArtistBoardPost post = getPost(artistId, postId);

		ArtistBoardComment comment = ArtistBoardComment.builder()
			.post(post)
			.parentComment(null)
			.authorType(ArtistBoardCommentAuthorType.MEMBER)
			.userId(userId)
			.artistId(null)
			.content(request.getContent().trim())
			.build();

		artistBoardCommentRepository.save(comment);
	}

	@Transactional
	public void createReply(Long artistId, Long postId, Long commentId, UUID userId, BoardRequest.CreateComment request) {
		validateBoardAccessible(artistId, userId);
		ArtistBoardPost post = getPost(artistId, postId);
		ArtistBoardComment parentComment = getComment(postId, commentId);

		ArtistBoardComment reply = ArtistBoardComment.builder()
			.post(post)
			.parentComment(parentComment)
			.authorType(ArtistBoardCommentAuthorType.MEMBER)
			.userId(userId)
			.artistId(null)
			.content(request.getContent().trim())
			.build();

		artistBoardCommentRepository.save(reply);
	}

	@Transactional
	public void likePost(Long artistId, Long postId, UUID userId) {
		validateBoardAccessible(artistId, userId);
		ArtistBoardPost post = getPost(artistId, postId);

		Preconditions.validate(
			!artistBoardPostLikeRepository.existsByUserIdAndPostId(userId, postId),
			ErrorCode.ALREADY_LIKED_ARTIST_BOARD_POST
		);

		ArtistBoardPostLike postLike = ArtistBoardPostLike.builder()
			.userId(userId)
			.post(post)
			.build();

		try {
			artistBoardPostLikeRepository.save(postLike);
		} catch (DataIntegrityViolationException e) {
			throw new com.truve.platform.common.exception.CustomException(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_POST);
		}
	}

	@Transactional
	public void unlikePost(Long artistId, Long postId, UUID userId) {
		validateBoardAccessible(artistId, userId);
		getPost(artistId, postId);
		artistBoardPostLikeRepository.deleteByUserIdAndPostId(userId, postId);
	}

	@Transactional
	public void likeComment(Long artistId, Long postId, Long commentId, UUID userId) {
		validateBoardAccessible(artistId, userId);
		getPost(artistId, postId);
		ArtistBoardComment comment = getComment(postId, commentId);

		Preconditions.validate(
			!artistBoardCommentLikeRepository.existsByUserIdAndCommentId(userId, commentId),
			ErrorCode.ALREADY_LIKED_ARTIST_BOARD_COMMENT
		);

		ArtistBoardCommentLike commentLike = ArtistBoardCommentLike.builder()
			.userId(userId)
			.comment(comment)
			.build();

		try {
			artistBoardCommentLikeRepository.save(commentLike);
		} catch (DataIntegrityViolationException e) {
			throw new com.truve.platform.common.exception.CustomException(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_COMMENT);
		}
	}

	@Transactional
	public void unlikeComment(Long artistId, Long postId, Long commentId, UUID userId) {
		validateBoardAccessible(artistId, userId);
		getPost(artistId, postId);
		getComment(postId, commentId);
		artistBoardCommentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
	}

	private void validateBoardAccessible(Long artistId, UUID userId) {
		ArtistResponse.BoardAccess boardAccess = artistService.getBoardAccess(artistId, userId);
		Preconditions.validate(Boolean.TRUE.equals(boardAccess.getAccessible()), ErrorCode.FORBIDDEN_ARTIST_BOARD_ACCESS);
	}

	private ArtistBoardPost getPost(Long artistId, Long postId) {
		return artistBoardPostRepository.findByIdAndArtistId(postId, artistId)
			.orElseThrow(() -> new com.truve.platform.common.exception.CustomException(ErrorCode.NOT_FOUND_ARTIST_BOARD_POST));
	}

	private ArtistBoardComment getComment(Long postId, Long commentId) {
		return artistBoardCommentRepository.findByIdAndPostId(commentId, postId)
			.orElseThrow(() -> new com.truve.platform.common.exception.CustomException(ErrorCode.NOT_FOUND_ARTIST_BOARD_COMMENT));
	}

	private BoardResponse.PostItem toPostItem(
		ArtistBoardPost post,
		Map<Long, Long> likeCounts,
		Map<Long, Long> commentCounts,
		Set<Long> likedPostIds
	) {
		Long postId = post.getId();

		return BoardResponse.PostItem.of(
			postId,
			post.getCreatedAt(),
			post.getArtist().getName(),
			toImageUrl(post.getArtist().getProfileImg()),
			post.getContent(),
			toImageUrls(post.getImageKeys()),
			likeCounts.getOrDefault(postId, 0L),
			commentCounts.getOrDefault(postId, 0L),
			likedPostIds.contains(postId)
		);
	}

	private List<ArtistBoardComment> getRootCommentsByFilter(Long postId, UUID userId, ArtistBoardCommentFilter filter) {
		return switch (filter) {
			case ALL -> artistBoardCommentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDescIdDesc(postId);
			case MINE -> artistBoardCommentRepository.findByPostIdAndParentCommentIsNullAndUserIdOrderByCreatedAtDescIdDesc(postId, userId);
			case ARTIST -> artistBoardCommentRepository.findByPostIdAndParentCommentIsNullAndAuthorTypeOrderByCreatedAtDescIdDesc(
				postId,
				ArtistBoardCommentAuthorType.ARTIST
			);
		};
	}

	private Map<Long, Long> findCommentLikeCounts(List<ArtistBoardComment> comments) {
		List<Long> commentIds = comments.stream()
			.map(ArtistBoardComment::getId)
			.toList();
		if (commentIds.isEmpty()) {
			return Map.of();
		}

		return artistBoardCommentLikeRepository.countLikesByCommentIds(commentIds).stream()
			.collect(Collectors.toMap(
				ArtistBoardCommentLikeRepository.CommentLikeCountProjection::getCommentId,
				ArtistBoardCommentLikeRepository.CommentLikeCountProjection::getLikeCount
			));
	}

	private Set<Long> findLikedCommentIds(List<ArtistBoardComment> comments, UUID userId) {
		if (userId == null || comments.isEmpty()) {
			return Set.of();
		}

		List<Long> commentIds = comments.stream()
			.map(ArtistBoardComment::getId)
			.toList();

		return artistBoardCommentLikeRepository.findLikedCommentIds(userId, commentIds).stream()
			.collect(Collectors.toSet());
	}

	private Map<Long, Long> findReplyCounts(List<ArtistBoardComment> comments) {
		List<Long> commentIds = comments.stream()
			.map(ArtistBoardComment::getId)
			.toList();
		if (commentIds.isEmpty()) {
			return Map.of();
		}

		return artistBoardCommentRepository.countRepliesByParentCommentIds(commentIds).stream()
			.collect(Collectors.toMap(
				ArtistBoardCommentRepository.ReplyCountProjection::getParentCommentId,
				ArtistBoardCommentRepository.ReplyCountProjection::getReplyCount
			));
	}

	private Map<UUID, User> findUsersByUserId(List<ArtistBoardComment> comments) {
		List<UUID> userIds = comments.stream()
			.filter(comment -> comment.getAuthorType() == ArtistBoardCommentAuthorType.MEMBER)
			.map(ArtistBoardComment::getUserId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();

		if (userIds.isEmpty()) {
			return Map.of();
		}

		return userRepository.findByUserIdIn(userIds).stream()
			.collect(Collectors.toMap(User::getUserId, user -> user));
	}

	private Map<Long, Artist> findArtistsByArtistId(List<ArtistBoardComment> comments) {
		List<Long> artistIds = comments.stream()
			.filter(comment -> comment.getAuthorType() == ArtistBoardCommentAuthorType.ARTIST)
			.map(ArtistBoardComment::getArtistId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();

		if (artistIds.isEmpty()) {
			return Map.of();
		}

		return artistRepository.findAllById(artistIds).stream()
			.collect(Collectors.toMap(Artist::getId, artist -> artist));
	}

	private BoardResponse.CommentItem toCommentItem(
		ArtistBoardComment comment,
		UUID userId,
		Map<UUID, User> usersByUserId,
		Map<Long, Artist> artistsByArtistId,
		Map<Long, Long> likeCounts,
		Set<Long> likedCommentIds,
		Map<Long, Long> replyCounts
	) {
		boolean isArtist = comment.getAuthorType() == ArtistBoardCommentAuthorType.ARTIST;
		boolean isMine = userId != null && userId.equals(comment.getUserId());
		Long commentId = comment.getId();

		return BoardResponse.CommentItem.of(
			commentId,
			comment.getCreatedAt(),
			resolveAuthorName(comment, usersByUserId, artistsByArtistId),
			resolveAuthorThumbnailUrl(comment, artistsByArtistId),
			comment.getContent(),
			likeCounts.getOrDefault(commentId, 0L),
			likedCommentIds.contains(commentId),
			replyCounts.getOrDefault(commentId, 0L),
			isMine,
			isArtist
		);
	}

	private String resolveAuthorName(
		ArtistBoardComment comment,
		Map<UUID, User> usersByUserId,
		Map<Long, Artist> artistsByArtistId
	) {
		if (comment.getAuthorType() == ArtistBoardCommentAuthorType.ARTIST) {
			Artist artist = artistsByArtistId.get(comment.getArtistId());
			if (artist != null) {
				return artist.getName();
			}
			return comment.getPost().getArtist().getName();
		}

		User user = usersByUserId.get(comment.getUserId());
		return user != null ? user.getNickname() : UNKNOWN_MEMBER_NAME;
	}

	private String resolveAuthorThumbnailUrl(ArtistBoardComment comment, Map<Long, Artist> artistsByArtistId) {
		if (comment.getAuthorType() != ArtistBoardCommentAuthorType.ARTIST) {
			return null;
		}

		Artist artist = artistsByArtistId.get(comment.getArtistId());
		if (artist != null) {
			return toImageUrl(artist.getProfileImg());
		}

		return toImageUrl(comment.getPost().getArtist().getProfileImg());
	}

	private String toImageUrl(String imageKey) {
		if (!StringUtils.hasText(imageKey)) {
			return null;
		}
		return s3Service.getImageUrl(imageKey);
	}

	private List<String> toImageUrls(List<String> imageKeys) {
		if (imageKeys == null || imageKeys.isEmpty()) {
			return Collections.emptyList();
		}

		return imageKeys.stream()
			.filter(StringUtils::hasText)
			.limit(MAX_IMAGE_COUNT)
			.map(this::toImageUrl)
			.filter(Objects::nonNull)
			.toList();
	}
}

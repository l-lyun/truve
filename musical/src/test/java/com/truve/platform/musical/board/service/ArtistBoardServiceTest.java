package com.truve.platform.musical.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentAuthorType;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentFilter;
import com.truve.platform.musical.board.domain.entity.ArtistBoardComment;
import com.truve.platform.musical.board.domain.entity.ArtistBoardPost;
import com.truve.platform.musical.board.repository.ArtistBoardCommentLikeRepository;
import com.truve.platform.musical.board.dto.BoardRequest;
import com.truve.platform.musical.board.dto.BoardResponse;
import com.truve.platform.musical.board.repository.ArtistBoardCommentRepository;
import com.truve.platform.musical.board.repository.ArtistBoardPostLikeRepository;
import com.truve.platform.musical.board.repository.ArtistBoardPostRepository;
import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.dto.ArtistResponse;
import com.truve.platform.musical.show.service.ArtistService;
import com.truve.platform.musical.user.domain.entity.User;
import com.truve.platform.musical.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ArtistBoardServiceTest {

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private ArtistBoardPostRepository artistBoardPostRepository;
	@Mock
	private ArtistBoardPostLikeRepository artistBoardPostLikeRepository;
	@Mock
	private ArtistBoardCommentRepository artistBoardCommentRepository;
	@Mock
	private ArtistBoardCommentLikeRepository artistBoardCommentLikeRepository;
	@Mock
	private ArtistService artistService;
	@Mock
	private S3Service s3Service;
	@Mock
	private UserRepository userRepository;
	@Mock
	private ArtistRepository artistRepository;

	@InjectMocks
	private ArtistBoardService artistBoardService;

	@Test
	@DisplayName("게시판 게시글 조회는 최신순 게시글 목록과 좋아요/댓글 수를 함께 응답한다.")
	void 게시판_게시글_조회_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		Artist artist = mock(Artist.class);
		ArtistBoardPostLikeRepository.PostLikeCountProjection likeCount = mock(
			ArtistBoardPostLikeRepository.PostLikeCountProjection.class
		);
		ArtistBoardCommentRepository.PostCommentCountProjection commentCount = mock(
			ArtistBoardCommentRepository.PostCommentCountProjection.class
		);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByArtistIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(post));
		when(post.getId()).thenReturn(10L);
		when(post.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 4, 12, 12, 0));
		when(post.getArtist()).thenReturn(artist);
		when(post.getContent()).thenReturn("게시글 내용입니다.");
		when(post.getImageKeys()).thenReturn(List.of("1.png", "2.png", "3.png", "4.png", "5.png"));
		when(artist.getName()).thenReturn("이재환");
		when(artist.getProfileImg()).thenReturn("artists/lee.png");
		when(likeCount.getPostId()).thenReturn(10L);
		when(likeCount.getLikeCount()).thenReturn(7L);
		when(commentCount.getPostId()).thenReturn(10L);
		when(commentCount.getCommentCount()).thenReturn(3L);
		when(artistBoardPostLikeRepository.countLikesByPostIds(List.of(10L))).thenReturn(List.of(likeCount));
		when(artistBoardCommentRepository.countCommentsByPostIds(List.of(10L))).thenReturn(List.of(commentCount));
		when(artistBoardPostLikeRepository.findLikedPostIds(USER_ID, List.of(10L))).thenReturn(List.of(10L));
		when(s3Service.getImageUrl("artists/lee.png")).thenReturn("https://img.example/artists/lee.png");
		when(s3Service.getImageUrl("1.png")).thenReturn("https://img.example/posts/1.png");
		when(s3Service.getImageUrl("2.png")).thenReturn("https://img.example/posts/2.png");
		when(s3Service.getImageUrl("3.png")).thenReturn("https://img.example/posts/3.png");
		when(s3Service.getImageUrl("4.png")).thenReturn("https://img.example/posts/4.png");

		BoardResponse.PostFeed response = artistBoardService.getPosts(1L, USER_ID);

		assertThat(response.getPosts()).hasSize(1);
		assertThat(response.getPosts().get(0).getPostId()).isEqualTo(10L);
		assertThat(response.getPosts().get(0).getArtistName()).isEqualTo("이재환");
		assertThat(response.getPosts().get(0).getArtistThumbnailUrl()).isEqualTo("https://img.example/artists/lee.png");
		assertThat(response.getPosts().get(0).getContent()).isEqualTo("게시글 내용입니다.");
		assertThat(response.getPosts().get(0).getImageUrls()).containsExactly(
			"https://img.example/posts/1.png",
			"https://img.example/posts/2.png",
			"https://img.example/posts/3.png",
			"https://img.example/posts/4.png"
		);
		assertThat(response.getPosts().get(0).getLikeCount()).isEqualTo(7L);
		assertThat(response.getPosts().get(0).getCommentCount()).isEqualTo(3L);
		assertThat(response.getPosts().get(0).isLikedByMe()).isTrue();
		verify(s3Service, never()).getImageUrl("5.png");
	}

	@Test
	@DisplayName("게시판 접근 권한이 없으면 게시글을 조회할 수 없다.")
	void 게시판_게시글_조회_권한없음_실패() {
		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(false)
				.accessible(false)
				.build()
		);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.getPosts(1L, USER_ID)
		);

		assertEquals(ErrorCode.FORBIDDEN_ARTIST_BOARD_ACCESS, exception.getErrorCode());
		verifyNoInteractions(artistBoardPostRepository, artistBoardPostLikeRepository, artistBoardCommentRepository);
	}

	@Test
	@DisplayName("댓글 조회는 필터와 카운트를 함께 응답한다.")
	void 댓글_조회_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		Artist boardArtist = mock(Artist.class);
		ArtistBoardComment memberComment = mock(ArtistBoardComment.class);
		ArtistBoardComment artistComment = mock(ArtistBoardComment.class);
		ArtistBoardCommentLikeRepository.CommentLikeCountProjection memberLikeCount = mock(
			ArtistBoardCommentLikeRepository.CommentLikeCountProjection.class
		);
		ArtistBoardCommentRepository.ReplyCountProjection memberReplyCount = mock(
			ArtistBoardCommentRepository.ReplyCountProjection.class
		);
		User user = User.builder()
			.userId(USER_ID)
			.nickname("멤버닉네임")
			.build();

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(post.getId()).thenReturn(10L);
		when(boardArtist.getId()).thenReturn(1L);
		when(boardArtist.getName()).thenReturn("이재환");
		when(boardArtist.getProfileImg()).thenReturn("artists/lee.png");
		when(artistBoardCommentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDescIdDesc(10L))
			.thenReturn(List.of(memberComment, artistComment));
		when(artistBoardCommentRepository.countByPostIdAndParentCommentIsNull(10L)).thenReturn(2L);
		when(artistBoardCommentRepository.countByPostIdAndParentCommentIsNullAndUserId(10L, USER_ID)).thenReturn(1L);
		when(artistBoardCommentRepository.countByPostIdAndParentCommentIsNullAndAuthorType(10L, ArtistBoardCommentAuthorType.ARTIST)).thenReturn(1L);
		when(userRepository.findByUserIdIn(List.of(USER_ID))).thenReturn(List.of(user));
		when(artistRepository.findAllById(List.of(1L))).thenReturn(List.of(boardArtist));
		when(s3Service.getImageUrl("artists/lee.png")).thenReturn("https://img.example/artists/lee.png");
		when(memberLikeCount.getCommentId()).thenReturn(101L);
		when(memberLikeCount.getLikeCount()).thenReturn(4L);
		when(memberReplyCount.getParentCommentId()).thenReturn(101L);
		when(memberReplyCount.getReplyCount()).thenReturn(2L);
		when(artistBoardCommentLikeRepository.countLikesByCommentIds(List.of(101L, 102L))).thenReturn(List.of(memberLikeCount));
		when(artistBoardCommentLikeRepository.findLikedCommentIds(USER_ID, List.of(101L, 102L))).thenReturn(List.of(101L));
		when(artistBoardCommentRepository.countRepliesByParentCommentIds(List.of(101L, 102L))).thenReturn(List.of(memberReplyCount));

		when(memberComment.getId()).thenReturn(101L);
		when(memberComment.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 4, 12, 13, 0));
		when(memberComment.getAuthorType()).thenReturn(ArtistBoardCommentAuthorType.MEMBER);
		when(memberComment.getUserId()).thenReturn(USER_ID);
		when(memberComment.getContent()).thenReturn("내 댓글");

		when(artistComment.getId()).thenReturn(102L);
		when(artistComment.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 4, 12, 12, 0));
		when(artistComment.getAuthorType()).thenReturn(ArtistBoardCommentAuthorType.ARTIST);
		when(artistComment.getUserId()).thenReturn(null);
		when(artistComment.getArtistId()).thenReturn(1L);
		when(artistComment.getContent()).thenReturn("아티스트 댓글");

		BoardResponse.CommentList response = artistBoardService.getComments(1L, 10L, USER_ID, ArtistBoardCommentFilter.ALL);

		assertThat(response.getSummary().getTotalCount()).isEqualTo(2L);
		assertThat(response.getSummary().getMyCount()).isEqualTo(1L);
		assertThat(response.getSummary().getArtistCount()).isEqualTo(1L);
		assertThat(response.getComments()).hasSize(2);
		assertThat(response.getComments().get(0).getAuthorName()).isEqualTo("멤버닉네임");
		assertThat(response.getComments().get(0).getLikeCount()).isEqualTo(4L);
		assertThat(response.getComments().get(0).isLikedByMe()).isTrue();
		assertThat(response.getComments().get(0).getReplyCount()).isEqualTo(2L);
		assertThat(response.getComments().get(0).isMine()).isTrue();
		assertThat(response.getComments().get(0).isArtist()).isFalse();
		assertThat(response.getComments().get(1).getAuthorName()).isEqualTo("이재환");
		assertThat(response.getComments().get(1).getAuthorThumbnailUrl()).isEqualTo("https://img.example/artists/lee.png");
		assertThat(response.getComments().get(1).getReplyCount()).isZero();
		assertThat(response.getComments().get(1).isMine()).isFalse();
		assertThat(response.getComments().get(1).isArtist()).isTrue();
	}

	@Test
	@DisplayName("답글 조회는 특정 댓글의 답글 목록과 댓글 좋아요 상태를 응답한다.")
	void 답글_조회_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		ArtistBoardComment parentComment = mock(ArtistBoardComment.class);
		ArtistBoardComment reply = mock(ArtistBoardComment.class);
		User user = User.builder()
			.userId(USER_ID)
			.nickname("테스트유저")
			.build();
		ArtistBoardCommentLikeRepository.CommentLikeCountProjection replyLikeCount = mock(
			ArtistBoardCommentLikeRepository.CommentLikeCountProjection.class
		);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardCommentRepository.findByIdAndPostId(101L, 10L)).thenReturn(java.util.Optional.of(parentComment));
		when(parentComment.getId()).thenReturn(101L);
		when(artistBoardCommentRepository.findByParentCommentIdOrderByCreatedAtDescIdDesc(101L)).thenReturn(List.of(reply));
		when(reply.getId()).thenReturn(201L);
		when(reply.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 4, 12, 14, 0));
		when(reply.getAuthorType()).thenReturn(ArtistBoardCommentAuthorType.MEMBER);
		when(reply.getUserId()).thenReturn(USER_ID);
		when(reply.getContent()).thenReturn("답글 내용");
		when(userRepository.findByUserIdIn(List.of(USER_ID))).thenReturn(List.of(user));
		when(replyLikeCount.getCommentId()).thenReturn(201L);
		when(replyLikeCount.getLikeCount()).thenReturn(2L);
		when(artistBoardCommentLikeRepository.countLikesByCommentIds(List.of(201L))).thenReturn(List.of(replyLikeCount));
		when(artistBoardCommentLikeRepository.findLikedCommentIds(USER_ID, List.of(201L))).thenReturn(List.of(201L));

		BoardResponse.ReplyList response = artistBoardService.getReplies(1L, 10L, 101L, USER_ID);

		assertThat(response.getReplies()).hasSize(1);
		assertThat(response.getReplies().get(0).getCommentId()).isEqualTo(201L);
		assertThat(response.getReplies().get(0).getLikeCount()).isEqualTo(2L);
		assertThat(response.getReplies().get(0).isLikedByMe()).isTrue();
		assertThat(response.getReplies().get(0).getReplyCount()).isZero();
	}

	@Test
	@DisplayName("댓글 작성은 멤버 댓글로 저장한다.")
	void 댓글_작성_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));

		artistBoardService.createComment(1L, 10L, USER_ID, new BoardRequest.CreateComment(" 댓글입니다. "));

		verify(artistBoardCommentRepository).save(any(ArtistBoardComment.class));
	}

	@Test
	@DisplayName("답글 작성은 부모 댓글이 연결된 답글로 저장한다.")
	void 답글_작성_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		ArtistBoardComment parentComment = mock(ArtistBoardComment.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardCommentRepository.findByIdAndPostId(101L, 10L)).thenReturn(java.util.Optional.of(parentComment));

		artistBoardService.createReply(1L, 10L, 101L, USER_ID, new BoardRequest.CreateComment(" 답글입니다. "));

		verify(artistBoardCommentRepository).save(any(ArtistBoardComment.class));
	}

	@Test
	@DisplayName("게시글 좋아요는 처음 요청일 때만 저장된다.")
	void 게시글_좋아요_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardPostLikeRepository.existsByUserIdAndPostId(USER_ID, 10L)).thenReturn(false);

		artistBoardService.likePost(1L, 10L, USER_ID);

		verify(artistBoardPostLikeRepository).save(any());
	}

	@Test
	@DisplayName("이미 좋아요한 게시글은 예외를 발생시킨다.")
	void 게시글_좋아요_중복_실패() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardPostLikeRepository.existsByUserIdAndPostId(USER_ID, 10L)).thenReturn(true);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.likePost(1L, 10L, USER_ID)
		);

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_POST, exception.getErrorCode());
		verify(artistBoardPostLikeRepository, never()).save(any());
	}

	@Test
	@DisplayName("좋아요 저장 중 중복 충돌이 나면 이미 좋아요 에러를 응답한다.")
	void 게시글_좋아요_동시성_중복충돌_실패() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardPostLikeRepository.existsByUserIdAndPostId(USER_ID, 10L)).thenReturn(false);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
			.when(artistBoardPostLikeRepository).save(any());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.likePost(1L, 10L, USER_ID)
		);

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_POST, exception.getErrorCode());
	}

	@Test
	@DisplayName("게시글 좋아요 취소는 사용자-게시글 기준으로 삭제한다.")
	void 게시글_좋아요_취소_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));

		artistBoardService.unlikePost(1L, 10L, USER_ID);

		verify(artistBoardPostLikeRepository).deleteByUserIdAndPostId(USER_ID, 10L);
	}

	@Test
	@DisplayName("댓글 좋아요는 처음 요청일 때만 저장된다.")
	void 댓글_좋아요_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		ArtistBoardComment comment = mock(ArtistBoardComment.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardCommentRepository.findByIdAndPostId(101L, 10L)).thenReturn(java.util.Optional.of(comment));
		when(artistBoardCommentLikeRepository.existsByUserIdAndCommentId(USER_ID, 101L)).thenReturn(false);

		artistBoardService.likeComment(1L, 10L, 101L, USER_ID);

		verify(artistBoardCommentLikeRepository).save(any());
	}

	@Test
	@DisplayName("이미 좋아요한 댓글은 예외를 발생시킨다.")
	void 댓글_좋아요_중복_실패() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		ArtistBoardComment comment = mock(ArtistBoardComment.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardCommentRepository.findByIdAndPostId(101L, 10L)).thenReturn(java.util.Optional.of(comment));
		when(artistBoardCommentLikeRepository.existsByUserIdAndCommentId(USER_ID, 101L)).thenReturn(true);

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.likeComment(1L, 10L, 101L, USER_ID)
		);

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_COMMENT, exception.getErrorCode());
	}

	@Test
	@DisplayName("댓글 좋아요 취소는 사용자-댓글 기준으로 삭제한다.")
	void 댓글_좋아요_취소_성공() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);
		ArtistBoardComment comment = mock(ArtistBoardComment.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardCommentRepository.findByIdAndPostId(101L, 10L)).thenReturn(java.util.Optional.of(comment));

		artistBoardService.unlikeComment(1L, 10L, 101L, USER_ID);

		verify(artistBoardCommentLikeRepository).deleteByUserIdAndCommentId(USER_ID, 101L);
	}

	@Test
	@DisplayName("존재하지 않는 게시글에 댓글을 조회하면 예외가 발생한다.")
	void 댓글_조회_게시글없음_실패() {
		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(999L, 1L)).thenReturn(java.util.Optional.empty());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.getComments(1L, 999L, USER_ID, ArtistBoardCommentFilter.ALL)
		);

		assertEquals(ErrorCode.NOT_FOUND_ARTIST_BOARD_POST, exception.getErrorCode());
	}

	@Test
	@DisplayName("존재하지 않는 댓글의 답글을 조회하면 예외가 발생한다.")
	void 답글_조회_댓글없음_실패() {
		ArtistBoardPost post = mock(ArtistBoardPost.class);

		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(ArtistResponse.BoardAccess.of(true, true));
		when(artistBoardPostRepository.findByIdAndArtistId(10L, 1L)).thenReturn(java.util.Optional.of(post));
		when(artistBoardCommentRepository.findByIdAndPostId(999L, 10L)).thenReturn(java.util.Optional.empty());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.getReplies(1L, 10L, 999L, USER_ID)
		);

		assertEquals(ErrorCode.NOT_FOUND_ARTIST_BOARD_COMMENT, exception.getErrorCode());
	}

	@Test
	@DisplayName("존재하지 않는 게시글에 좋아요를 요청하면 예외가 발생한다.")
	void 게시글_좋아요_게시글없음_실패() {
		when(artistService.getBoardAccess(1L, USER_ID)).thenReturn(
			ArtistResponse.BoardAccess.builder()
				.joined(true)
				.accessible(true)
				.build()
		);
		when(artistBoardPostRepository.findByIdAndArtistId(999L, 1L)).thenReturn(java.util.Optional.empty());

		CustomException exception = assertThrows(
			CustomException.class,
			() -> artistBoardService.likePost(1L, 999L, USER_ID)
		);

		assertEquals(ErrorCode.NOT_FOUND_ARTIST_BOARD_POST, exception.getErrorCode());
	}
}

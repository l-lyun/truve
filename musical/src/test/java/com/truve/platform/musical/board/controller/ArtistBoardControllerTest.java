package com.truve.platform.musical.board.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentFilter;
import com.truve.platform.musical.board.dto.BoardRequest;
import com.truve.platform.musical.board.dto.BoardResponse;
import com.truve.platform.musical.board.service.ArtistBoardService;

@WebMvcTest(controllers = ArtistBoardController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class ArtistBoardControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ArtistBoardService artistBoardService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("게시판 게시글 조회에 성공하면 200 OK와 게시글 목록을 응답한다.")
	void 게시판_게시글_조회_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BoardResponse.PostFeed response = BoardResponse.PostFeed.builder()
			.posts(List.of(
				BoardResponse.PostItem.builder()
					.postId(10L)
					.createdAt(LocalDateTime.of(2026, 4, 12, 12, 0))
					.artistName("이재환")
					.artistThumbnailUrl("https://img.example/artist.png")
					.content("게시글 내용")
					.imageUrls(List.of("https://img.example/post-1.png", "https://img.example/post-2.png"))
					.likeCount(7L)
					.commentCount(3L)
					.likedByMe(true)
					.build()
			))
			.build();

		given(artistBoardService.getPosts(1L, userId)).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}/board", 1L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.posts[0].postId").value(10))
			.andExpect(jsonPath("$.data.posts[0].artistName").value("이재환"))
			.andExpect(jsonPath("$.data.posts[0].imageUrls[0]").value("https://img.example/post-1.png"))
			.andExpect(jsonPath("$.data.posts[0].likeCount").value(7))
			.andExpect(jsonPath("$.data.posts[0].commentCount").value(3))
			.andExpect(jsonPath("$.data.posts[0].likedByMe").value(true));
	}

	@Test
	@DisplayName("게시판 접근 권한이 없으면 403을 응답한다.")
	void 게시판_게시글_조회_권한없음_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.FORBIDDEN_ARTIST_BOARD_ACCESS))
			.given(artistBoardService).getPosts(anyLong(), nullable(UUID.class));

		mockMvc.perform(get("/api/musical/artists/{artistId}/board", 1L))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M05"));
	}

	@Test
	@DisplayName("존재하지 않는 아티스트 게시판 게시글 조회는 404를 응답한다.")
	void 게시판_게시글_조회_아티스트없음_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST))
			.given(artistBoardService).getPosts(anyLong(), nullable(UUID.class));

		mockMvc.perform(get("/api/musical/artists/{artistId}/board", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M02"));
	}

	@Test
	@DisplayName("게시글 댓글 조회에 성공하면 200 OK와 댓글 목록을 응답한다.")
	void 게시글_댓글_조회_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BoardResponse.CommentList response = BoardResponse.CommentList.of(
			BoardResponse.CommentSummary.of(4L, 2L, 1L),
			List.of(
				BoardResponse.CommentItem.of(
					101L,
					LocalDateTime.of(2026, 4, 12, 13, 0),
					"멤버닉네임",
					null,
					"댓글 내용",
					5L,
					true,
					2L,
					true,
					false
				)
			)
		);

		given(artistBoardService.getComments(1L, 10L, userId, ArtistBoardCommentFilter.ALL)).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}/board/{postId}/comments", 1L, 10L)
				.header("X-User-Id", userId)
				.param("filter", "ALL"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.summary.totalCount").value(4))
			.andExpect(jsonPath("$.data.summary.myCount").value(2))
			.andExpect(jsonPath("$.data.summary.artistCount").value(1))
			.andExpect(jsonPath("$.data.comments[0].commentId").value(101))
			.andExpect(jsonPath("$.data.comments[0].authorName").value("멤버닉네임"))
			.andExpect(jsonPath("$.data.comments[0].likeCount").value(5))
			.andExpect(jsonPath("$.data.comments[0].likedByMe").value(true))
			.andExpect(jsonPath("$.data.comments[0].replyCount").value(2))
			.andExpect(jsonPath("$.data.comments[0].isMine").value(true))
			.andExpect(jsonPath("$.data.comments[0].isArtist").value(false));
	}

	@Test
	@DisplayName("답글 조회에 성공하면 200 OK와 답글 목록을 응답한다.")
	void 답글_조회_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BoardResponse.ReplyList response = BoardResponse.ReplyList.of(
			List.of(
				BoardResponse.CommentItem.of(
					201L,
					LocalDateTime.of(2026, 4, 12, 14, 0),
					"테스트유저",
					null,
					"답글 내용",
					1L,
					false,
					0L,
					true,
					false
				)
			)
		);

		given(artistBoardService.getReplies(1L, 10L, 101L, userId)).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}/board/{postId}/comments/{commentId}/replies", 1L, 10L, 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.replies[0].commentId").value(201))
			.andExpect(jsonPath("$.data.replies[0].likeCount").value(1))
			.andExpect(jsonPath("$.data.replies[0].replyCount").value(0));
	}

	@Test
	@DisplayName("게시글 댓글 작성에 성공하면 200 OK를 응답한다.")
	void 게시글_댓글_작성_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BoardRequest.CreateComment request = new BoardRequest.CreateComment("댓글 작성");

		willDoNothing().given(artistBoardService).createComment(1L, 10L, userId, request);

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/comments", 1L, 10L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("게시글 답글 작성에 성공하면 200 OK를 응답한다.")
	void 게시글_답글_작성_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BoardRequest.CreateComment request = new BoardRequest.CreateComment("답글 작성");

		willDoNothing().given(artistBoardService).createReply(1L, 10L, 101L, userId, request);

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/comments/{commentId}/replies", 1L, 10L, 101L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("빈 댓글 내용으로 작성하면 400(C02)을 응답한다.")
	void 게시글_댓글_작성_검증_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BoardRequest.CreateComment request = new BoardRequest.CreateComment(" ");

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/comments", 1L, 10L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C02"));
	}

	@Test
	@DisplayName("존재하지 않는 게시글 댓글 조회는 404를 응답한다.")
	void 게시글_댓글_조회_게시글없음_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST_BOARD_POST))
			.given(artistBoardService).getComments(anyLong(), anyLong(), nullable(UUID.class), org.mockito.ArgumentMatchers.any());

		mockMvc.perform(get("/api/musical/artists/{artistId}/board/{postId}/comments", 1L, 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M06"));
	}

	@Test
	@DisplayName("존재하지 않는 댓글 답글 조회는 404를 응답한다.")
	void 답글_조회_댓글없음_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST_BOARD_COMMENT))
			.given(artistBoardService).getReplies(anyLong(), anyLong(), anyLong(), nullable(UUID.class));

		mockMvc.perform(get("/api/musical/artists/{artistId}/board/{postId}/comments/{commentId}/replies", 1L, 10L, 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("M08"));
	}

	@Test
	@DisplayName("게시글 좋아요 등록에 성공하면 200 OK를 응답한다.")
	void 게시글_좋아요_등록_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willDoNothing().given(artistBoardService).likePost(1L, 10L, userId);

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/likes", 1L, 10L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("이미 좋아요한 게시글은 400을 응답한다.")
	void 게시글_좋아요_등록_중복_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willThrow(new CustomException(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_POST))
			.given(artistBoardService).likePost(1L, 10L, userId);

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/likes", 1L, 10L)
				.header("X-User-Id", userId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M07"));
	}

	@Test
	@DisplayName("게시글 좋아요 취소에 성공하면 200 OK를 응답한다.")
	void 게시글_좋아요_취소_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willDoNothing().given(artistBoardService).unlikePost(1L, 10L, userId);

		mockMvc.perform(delete("/api/musical/artists/{artistId}/board/{postId}/likes", 1L, 10L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("댓글 좋아요 등록에 성공하면 200 OK를 응답한다.")
	void 댓글_좋아요_등록_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willDoNothing().given(artistBoardService).likeComment(1L, 10L, 101L, userId);

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/comments/{commentId}/likes", 1L, 10L, 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("이미 좋아요한 댓글은 400을 응답한다.")
	void 댓글_좋아요_등록_중복_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willThrow(new CustomException(ErrorCode.ALREADY_LIKED_ARTIST_BOARD_COMMENT))
			.given(artistBoardService).likeComment(1L, 10L, 101L, userId);

		mockMvc.perform(post("/api/musical/artists/{artistId}/board/{postId}/comments/{commentId}/likes", 1L, 10L, 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("M09"));
	}

	@Test
	@DisplayName("댓글 좋아요 취소에 성공하면 200 OK를 응답한다.")
	void 댓글_좋아요_취소_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willDoNothing().given(artistBoardService).unlikeComment(1L, 10L, 101L, userId);

		mockMvc.perform(delete("/api/musical/artists/{artistId}/board/{postId}/comments/{commentId}/likes", 1L, 10L, 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}
}

package com.truve.platform.musical.board.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.board.domain.constant.ArtistBoardCommentFilter;
import com.truve.platform.musical.board.dto.BoardRequest;
import com.truve.platform.musical.board.dto.BoardResponse;
import com.truve.platform.musical.board.service.ArtistBoardService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musical/artists/{artistId}/board")
public class ArtistBoardController {

	private final ArtistBoardService artistBoardService;

	@Operation(summary = "아티스트 게시판 게시글 조회", description = "멤버십 가입 사용자가 아티스트 게시판 게시글 목록을 최신순으로 조회합니다.")
	@GetMapping
	public ApiResult<BoardResponse.PostFeed> getPosts(
		@PathVariable Long artistId,
		@RequestHeader(name = "X-User-Id", required = false) UUID userId
	) {
		return ApiResult.ok(artistBoardService.getPosts(artistId, userId));
	}

	@Operation(summary = "아티스트 게시판 댓글 조회", description = "멤버십 가입 사용자가 게시글 댓글을 최신순으로 조회합니다.")
	@GetMapping("/{postId}/comments")
	public ApiResult<BoardResponse.CommentList> getComments(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@RequestHeader(name = "X-User-Id", required = false) UUID userId,
		@RequestParam(defaultValue = "ALL") ArtistBoardCommentFilter filter
	) {
		return ApiResult.ok(artistBoardService.getComments(artistId, postId, userId, filter));
	}

	@Operation(summary = "아티스트 게시판 댓글 작성", description = "멤버십 가입 사용자가 게시글에 댓글을 작성합니다.")
	@PostMapping("/{postId}/comments")
	public ApiResult<Void> createComment(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@RequestHeader(name = "X-User-Id") UUID userId,
		@RequestBody @Valid BoardRequest.CreateComment request
	) {
		artistBoardService.createComment(artistId, postId, userId, request);
		return ApiResult.ok();
	}

	@Operation(summary = "아티스트 게시판 답글 조회", description = "멤버십 가입 사용자가 특정 댓글의 답글 목록을 최신순으로 조회합니다.")
	@GetMapping("/{postId}/comments/{commentId}/replies")
	public ApiResult<BoardResponse.ReplyList> getReplies(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@PathVariable Long commentId,
		@RequestHeader(name = "X-User-Id", required = false) UUID userId
	) {
		return ApiResult.ok(artistBoardService.getReplies(artistId, postId, commentId, userId));
	}

	@Operation(summary = "아티스트 게시판 답글 작성", description = "멤버십 가입 사용자가 특정 댓글에 답글을 작성합니다.")
	@PostMapping("/{postId}/comments/{commentId}/replies")
	public ApiResult<Void> createReply(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@PathVariable Long commentId,
		@RequestHeader(name = "X-User-Id") UUID userId,
		@RequestBody @Valid BoardRequest.CreateComment request
	) {
		artistBoardService.createReply(artistId, postId, commentId, userId, request);
		return ApiResult.ok();
	}

	@Operation(summary = "아티스트 게시판 댓글 좋아요", description = "멤버십 가입 사용자가 아티스트 댓글에 좋아요를 등록합니다.")
	@PostMapping("/{postId}/comments/{commentId}/likes")
	public ApiResult<Void> likeComment(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@PathVariable Long commentId,
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		artistBoardService.likeComment(artistId, postId, commentId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "아티스트 게시판 댓글 좋아요 취소", description = "멤버십 가입 사용자가 아티스트 댓글 좋아요를 취소합니다.")
	@DeleteMapping("/{postId}/comments/{commentId}/likes")
	public ApiResult<Void> unlikeComment(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@PathVariable Long commentId,
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		artistBoardService.unlikeComment(artistId, postId, commentId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "아티스트 게시판 게시글 좋아요", description = "멤버십 가입 사용자가 아티스트 게시글에 좋아요를 등록합니다.")
	@PostMapping("/{postId}/likes")
	public ApiResult<Void> likePost(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		artistBoardService.likePost(artistId, postId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "아티스트 게시판 게시글 좋아요 취소", description = "멤버십 가입 사용자가 아티스트 게시글 좋아요를 취소합니다.")
	@DeleteMapping("/{postId}/likes")
	public ApiResult<Void> unlikePost(
		@PathVariable Long artistId,
		@PathVariable Long postId,
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		artistBoardService.unlikePost(artistId, postId, userId);
		return ApiResult.ok();
	}
}

package com.truve.platform.musical.show.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.PageResponse;
import com.truve.platform.common.response.Paging;
import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.show.dto.ArtistResponse;
import com.truve.platform.musical.show.service.ArtistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musical/artists")
public class ArtistController {

	private final ArtistService artistService;

	@Operation(summary = "배우 좋아요 등록", description = "로그인 사용자가 배우 좋아요를 등록합니다.")
	@PostMapping("/{artistId}/likes")
	public ApiResult<Void> likeArtist(
		@PathVariable Long artistId,
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		artistService.likeArtist(artistId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "배우 좋아요 취소", description = "로그인 사용자가 배우 좋아요를 취소합니다.")
	@DeleteMapping("/{artistId}/likes")
	public ApiResult<Void> unlikeArtist(
		@PathVariable Long artistId,
		@RequestHeader(name = "X-User-Id") UUID userId
	) {
		artistService.unlikeArtist(artistId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "아티스트 상세 조회", description = "아티스트 페이지 정보를 조회합니다.")
	@GetMapping("/{artistId}")
	public ApiResult<ArtistResponse.Detail> getArtistDetail(
		@PathVariable Long artistId,
		@RequestHeader(name = "X-User-Id", required = false) UUID userId
	) {
		return ApiResult.ok(artistService.getDetail(artistId, userId));
	}

	@Operation(summary = "아티스트 지난 출연 작품 조회", description = "아티스트의 지난 출연 작품 목록을 조회합니다.")
	@GetMapping("/{artistId}/past-shows")
	public ApiResult<PageResponse<ArtistResponse.ShowSummary>> getPastShows(
		@PathVariable Long artistId,
		@Parameter(hidden = true)
		@Valid @ModelAttribute Paging paging
	) {
		return ApiResult.ok(artistService.getPastShows(artistId, paging));
	}
}
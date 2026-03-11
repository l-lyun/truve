package com.truve.platform.musical.show.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.show.service.ArtistService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/artists")
public class ArtistLikeController {

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
}

package com.truve.platform.musical.show.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.service.ShowService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shows")
public class ShowController {

	private final ShowService showService;

	@Operation(summary = "공연 상세 조회", description = "공연 상세 정보를 조회합니다.")
	@GetMapping("/{showId}")
	public ApiResult<ShowResponse.Detail> getDetail(
		@PathVariable Long showId,
		@RequestHeader(name = "X-User-Id", required = false) Long userId
	) {
		return ApiResult.ok(showService.getDetail(showId, userId));
	}
}

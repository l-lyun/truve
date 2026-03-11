package com.truve.platform.musical.show.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.show.dto.ShowCastingResponse;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.service.ShowCastingService;
import com.truve.platform.musical.show.service.ShowDetailService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shows")
public class ShowController {

	private final ShowDetailService showDetailService;
	private final ShowCastingService showCastingService;

	@Operation(summary = "공연 상세 조회", description = "공연 상세 정보를 조회합니다.")
	@GetMapping("/{showId}")
	public ApiResult<ShowResponse.Detail> getDetail(
		@PathVariable Long showId,
		@RequestHeader(name = "X-User-Id", required = false) UUID userId
	) {
		return ApiResult.ok(showDetailService.getDetail(showId, userId));
	}

	@Operation(summary = "공연 캐스팅 일정 조회", description = "공연 회차별 캐스팅 일정을 조회합니다.")
	@GetMapping("/{showId}/casting-schedules")
	public ApiResult<ShowCastingResponse.Detail> getCastingSchedules(
		@PathVariable Long showId,
		@RequestParam(name = "from", required = false) LocalDate from,
		@RequestParam(name = "to", required = false) LocalDate to,
		@RequestParam(name = "artistIds", required = false) List<Long> artistIds,
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "50") int size
	) {
		return ApiResult.ok(showCastingService.getCastingSchedules(showId, from, to, artistIds, page, size));
	}
}

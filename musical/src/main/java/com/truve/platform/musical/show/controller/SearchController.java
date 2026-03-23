package com.truve.platform.musical.show.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.show.dto.SearchResponse;
import com.truve.platform.musical.show.service.SearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musical")
public class SearchController {

	private final SearchService searchService;

	@Operation(
		summary = "통합 검색",
		description = "공연명/배우명으로 통합 검색 결과를 조회합니다."
	)
	@GetMapping("/search")
	public ApiResult<SearchResponse.SearchResult> search(
		@Parameter(description = "검색어(공백 입력 시 빈 결과 반환)")
		@RequestParam(name = "keyword", required = false) String keyword,
		@Parameter(description = "아티스트 검색 시작 위치")
		@RequestParam(name = "artistOffset", defaultValue = "0") int artistOffset,
		@Parameter(description = "아티스트 한 페이지 개수")
		@RequestParam(name = "artistLimit", defaultValue = "20") int artistLimit,
		@Parameter(description = "공연 검색 시작 위치")
		@RequestParam(name = "showOffset", defaultValue = "0") int showOffset,
		@Parameter(description = "공연 한 페이지 개수")
		@RequestParam(name = "showLimit", defaultValue = "20") int showLimit
	) {
		return ApiResult.ok(searchService.search(keyword, artistOffset, artistLimit, showOffset, showLimit));
	}
}
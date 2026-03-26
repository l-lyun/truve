package com.truve.platform.musical.show.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.show.domain.constant.HomeRegion;
import com.truve.platform.musical.show.domain.constant.HomeShowOrder;
import com.truve.platform.musical.show.dto.HomeResponse;
import com.truve.platform.musical.show.service.HomeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musical/home")
public class HomeController {

	private final HomeService homeService;

	@Operation(
		summary = "홈 공연 목록 조회",
		description = "홈 화면 공연 목록을 조회합니다. 기본 정렬은 DAILY_BOOKING, 기본 지역은 ALL입니다.",
		parameters = {
			@Parameter(
				name = "order",
				description = "정렬 기준 (DAILY_BOOKING=일간 예매순, WEEKLY_BOOKING=주간 예매순, ENDING_SOON=종료 임박 순, MOST_REVIEWED=리뷰 많은 순)"
			),
			@Parameter(
				name = "region",
				description = "지역 필터 (ALL=전체, SEOUL=서울, GYEONGGI=경기, GANGWON=강원, CHUNGCHEONG=충청, JEOLLA=전라, GYEONGSANG=경상, JEJU=제주)"
			),
			@Parameter(name = "page", description = "페이지 번호(기본 1)", example = "1"),
			@Parameter(name = "size", description = "페이지 크기(기본 10)", example = "10")
		}
	)
	@GetMapping("/shows")
	public ApiResult<HomeResponse.ShowList> getHomeShows(
		@RequestParam(name = "order", required = false) HomeShowOrder order,
		@RequestParam(name = "region", required = false) HomeRegion region,
		@Parameter(hidden = true)
		@Valid @ModelAttribute Paging paging
	) {
		return ApiResult.ok(homeService.getHomeShows(order, region, paging));
	}

	@Operation(summary = "홈 배너 조회", description = "홈 화면 상단 배너 목록을 조회합니다.")
	@GetMapping("/banners")
	public ApiResult<HomeResponse.BannerList> getHomeBanners() {
		return ApiResult.ok(homeService.getHomeBanners());
	}

	@Operation(summary = "홈 프로모션 배너 조회", description = "홈 화면 프로모션 배너 목록을 조회합니다.")
	@GetMapping("/promotions")
	public ApiResult<HomeResponse.PromotionShowList> getPromotions() {
		return ApiResult.ok(homeService.getPromotionShows());
	}
}
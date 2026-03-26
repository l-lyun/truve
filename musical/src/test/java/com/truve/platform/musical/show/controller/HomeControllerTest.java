package com.truve.platform.musical.show.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.show.domain.constant.HomeRegion;
import com.truve.platform.musical.show.dto.HomeResponse;
import com.truve.platform.musical.show.service.HomeService;

@WebMvcTest(controllers = HomeController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class HomeControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private HomeService homeService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("홈 공연 목록 조회에 성공하면 200과 목록을 응답한다.")
	void 홈_공연_목록_조회_성공() throws Exception {
			HomeResponse.ShowList response = HomeResponse.ShowList.builder()
				.shows(List.of(
					HomeResponse.ShowSummary.builder()
						.showId(1L)
						.posterUrl("https://img.example/show1.jpg")
						.showTitle("Wicked")
						.venueName("샤롯데씨어터")
						.date("2025.11.29 - 2026.02.22")
					.build()
			))
			.page(HomeResponse.Page.builder()
				.currentPage(1)
				.size(10)
				.totalElements(1)
				.totalPages(1)
				.build())
			.build();

			given(homeService.getHomeShows(
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.isNull(),
				argThat((Paging p) -> p != null && p.getPage() == 1 && p.getSize() == 10)
			))
				.willReturn(response);

		mockMvc.perform(get("/api/musical/home/shows")
				.param("page", "1")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.shows[0].showId").value(1))
			.andExpect(jsonPath("$.data.shows[0].showTitle").value("Wicked"))
			.andExpect(jsonPath("$.data.page.currentPage").value(1));
	}

	@Test
	@DisplayName("홈 공연 목록 조회에서 region enum 상수 값으로 조회된다.")
	void 홈_공연_목록_조회_지역_enum값_성공() throws Exception {
		HomeResponse.ShowList response = HomeResponse.ShowList.builder()
			.shows(List.of())
			.page(HomeResponse.Page.builder()
				.currentPage(0)
				.size(20)
				.totalElements(0)
				.totalPages(0)
				.build())
			.build();

		given(homeService.getHomeShows(
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(HomeRegion.SEOUL),
			argThat((Paging p) -> p != null && p.getPage() == 1 && p.getSize() == 20)
		)).willReturn(response);

		mockMvc.perform(get("/api/musical/home/shows")
				.param("region", "SEOUL")
				.param("page", "1")
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("홈 배너 조회에 성공하면 200과 배너 목록을 응답한다.")
	void 홈_배너_조회_성공() throws Exception {
		HomeResponse.BannerList response = HomeResponse.BannerList.builder()
			.banners(List.of(
				HomeResponse.Banner.builder()
					.bannerId(11L)
					.showId(1L)
					.showTitle("Wicked")
					.venueName("샤롯데씨어터")
					.date("2026.03.01 - 2026.05.31")
					.posterUrl("https://img.example/home/banner1.jpg")
					.displayOrder(1)
					.build()
			))
			.build();

		given(homeService.getHomeBanners()).willReturn(response);

		mockMvc.perform(get("/api/musical/home/banners"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.banners[0].bannerId").value(11))
			.andExpect(jsonPath("$.data.banners[0].showId").value(1))
			.andExpect(jsonPath("$.data.banners[0].showTitle").value("Wicked"))
			.andExpect(jsonPath("$.data.banners[0].venueName").value("샤롯데씨어터"))
				.andExpect(jsonPath("$.data.banners[0].date").value("2026.03.01 - 2026.05.31"))
				.andExpect(jsonPath("$.data.banners[0].posterUrl").value("https://img.example/home/banner1.jpg"));
	}

	@Test
	@DisplayName("홈 프로모션 배너 조회에 성공하면 200과 프로모션 배너 목록을 응답한다.")
	void 홈_프로모션_배너_조회_성공() throws Exception {
		HomeResponse.PromotionShowList response = HomeResponse.PromotionShowList.builder()
			.totalCount(2)
			.shows(List.of(
				HomeResponse.PromotionShow.builder()
					.displayOrder(1)
					.showId(1L)
					.posterUrl("https://img.example/promotion/banner1.jpg")
					.build()
			))
			.build();

		given(homeService.getPromotionShows()).willReturn(response);

		mockMvc.perform(get("/api/musical/home/promotions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.totalCount").value(2))
			.andExpect(jsonPath("$.data.shows[0].displayOrder").value(1))
			.andExpect(jsonPath("$.data.shows[0].showId").value(1))
			.andExpect(jsonPath("$.data.shows[0].posterUrl").value("https://img.example/promotion/banner1.jpg"));
	}

	@Test
	@DisplayName("홈 공연 목록 조회에서 size가 0 이하면 400(C02)을 응답한다.")
	void 홈_공연_목록_조회_검증_실패() throws Exception {
		mockMvc.perform(get("/api/musical/home/shows")
				.param("page", "1")
				.param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C02"));
	}

	@Test
	@DisplayName("홈 공연 목록 조회에서 지원하지 않는 지역 값이면 500(C01)을 응답한다.")
	void 홈_공연_목록_조회_잘못된_지역_실패() throws Exception {
		mockMvc.perform(get("/api/musical/home/shows")
				.param("region", "부산")
				.param("page", "0")
				.param("size", "20"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("C01"));

		verifyNoInteractions(homeService);
	}
}

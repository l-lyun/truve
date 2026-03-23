package com.truve.platform.musical.show.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.show.dto.SearchResponse;
import com.truve.platform.musical.show.service.SearchService;

@WebMvcTest(controllers = SearchController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class SearchControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private SearchService searchService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("검색 조회에 성공하면 200과 검색 결과를 응답한다.")
	void 검색_조회_성공() throws Exception {
		SearchResponse.SearchResult response = SearchResponse.SearchResult.builder()
			.keyword("김호")
			.artistCount(2)
			.showCount(1)
			.hasMoreArtists(false)
			.hasMoreShows(false)
			.artists(List.of(
				SearchResponse.ArtistSummary.builder()
					.artistId(101L)
					.artistName("김호영")
					.profileImageUrl(null)
					.appearanceInfo("출연: 뮤지컬 <테스트 검색 공연>(2026)")
					.build(),
					SearchResponse.ArtistSummary.builder()
						.artistId(109L)
						.artistName("김호석")
						.profileImageUrl(null)
						.appearanceInfo("출연 정보 없음")
						.build()
			))
			.shows(List.of(
				SearchResponse.ShowSummary.builder()
					.showId(6L)
					.posterUrl("http://localstack:4566/truve-media/shows/search-test-poster.jpg")
					.title("테스트 검색 공연")
					.venueName("Blue Square")
					.date("2026.03.20 - 2026.06.30")
					.build()
			))
			.build();

		given(searchService.search("김호", 0, 20, 0, 20)).willReturn(response);

		mockMvc.perform(get("/api/musical/search").param("keyword", "김호"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.keyword").value("김호"))
			.andExpect(jsonPath("$.data.artistCount").value(2))
			.andExpect(jsonPath("$.data.showCount").value(1))
			.andExpect(jsonPath("$.data.hasMoreArtists").value(false))
			.andExpect(jsonPath("$.data.hasMoreShows").value(false))
			.andExpect(jsonPath("$.data.artists[0].artistName").value("김호영"))
			.andExpect(jsonPath("$.data.shows[0].title").value("테스트 검색 공연"));
	}

	@Test
	@DisplayName("검색 처리 중 예외가 발생하면 공통 500(C01)을 응답한다.")
	void 검색_조회_실패_서버에러() throws Exception {
		willThrow(new RuntimeException("db error"))
			.given(searchService).search("김호", 0, 20, 0, 20);

		mockMvc.perform(get("/api/musical/search").param("keyword", "김호"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("C01"))
			.andExpect(jsonPath("$.errorType").value("SERVER_ERROR"));
	}
}
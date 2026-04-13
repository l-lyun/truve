package com.truve.platform.musical.show.controller;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.show.dto.ArtistResponse;
import com.truve.platform.musical.show.service.ArtistService;

@WebMvcTest(controllers = ArtistController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class ArtistControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ArtistService artistService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("배우 좋아요 등록에 성공하면 200 OK를 응답한다.")
	void 배우_좋아요_등록_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willDoNothing().given(artistService).likeArtist(101L, userId);

		mockMvc.perform(post("/api/musical/artists/{artistId}/likes", 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("배우 좋아요 취소에 성공하면 200 OK를 응답한다.")
	void 배우_좋아요_취소_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		willDoNothing().given(artistService).unlikeArtist(101L, userId);

		mockMvc.perform(delete("/api/musical/artists/{artistId}/likes", 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("아티스트 상세 조회에 성공하면 200 OK와 페이지 정보를 응답한다.")
	void 아티스트_상세_조회_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		ArtistResponse.Detail response = ArtistResponse.Detail.builder()
			.artist(ArtistResponse.Artist.builder()
				.artistId(1L)
				.artistName("이재환")
				.profileImageUrl("https://img.example/artist.jpg")
				.isLiked(true)
				.build())
			.membership(ArtistResponse.Membership.builder()
				.joined(true)
				.build())
			.notices(List.of(
				ArtistResponse.Notice.builder()
					.noticeId(10L)
					.content("공지")
					.build()
			))
			.currentShows(List.of(
				ArtistResponse.ShowSummary.builder()
					.showId(1L)
					.showTitle("킹키부츠")
					.posterUrl("https://img.example/poster.jpg")
					.venueName("샤롯데씨어터")
					.startTime(LocalDateTime.of(2026, 3, 11, 0, 0))
					.endTime(LocalDateTime.of(2026, 6, 21, 0, 0))
					.date("2026.03.11 - 2026.06.21")
					.build()
			))
			.pastShows(ArtistResponse.PastShowSection.builder()
				.shows(List.of(
					ArtistResponse.ShowSummary.builder()
						.showId(2L)
						.showTitle("지킬앤하이드")
						.posterUrl("https://img.example/past.jpg")
						.venueName("블루스퀘어")
						.startTime(LocalDateTime.of(2025, 1, 1, 0, 0))
						.endTime(LocalDateTime.of(2025, 2, 1, 0, 0))
						.date("2025.01.01 - 2025.02.01")
						.build()
				))
				.hasMore(false)
				.build())
			.build();

		given(artistService.getDetail(anyLong(), nullable(UUID.class))).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}", 1L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.artist.artistName").value("이재환"))
			.andExpect(jsonPath("$.data.artist.isLiked").value(true))
			.andExpect(jsonPath("$.data.membership.joined").value(true))
			.andExpect(jsonPath("$.data.notices[0].noticeId").value(10))
			.andExpect(jsonPath("$.data.currentShows[0].showTitle").value("킹키부츠"))
			.andExpect(jsonPath("$.data.pastShows.shows[0].showTitle").value("지킬앤하이드"))
			.andExpect(jsonPath("$.data.pastShows.hasMore").value(false));
	}

	@Test
	@DisplayName("존재하지 않는 아티스트 상세를 조회하면 404를 응답한다.")
	void 아티스트_상세_조회_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST))
			.given(artistService).getDetail(anyLong(), nullable(UUID.class));

		mockMvc.perform(get("/api/musical/artists/{artistId}", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M02"));
	}

	@Test
	@DisplayName("아티스트 게시판 접근 가능 여부 조회에 성공하면 200 OK를 응답한다.")
	void 아티스트_게시판_접근가능여부_조회_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		ArtistResponse.BoardAccess response = ArtistResponse.BoardAccess.builder()
			.joined(true)
			.accessible(true)
			.build();

		given(artistService.getBoardAccess(1L, userId)).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}/board/access", 1L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.joined").value(true))
			.andExpect(jsonPath("$.data.accessible").value(true));
	}

	@Test
	@DisplayName("존재하지 않는 아티스트 게시판 접근 여부를 조회하면 404를 응답한다.")
	void 아티스트_게시판_접근가능여부_조회_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST))
			.given(artistService).getBoardAccess(anyLong(), nullable(UUID.class));

		mockMvc.perform(get("/api/musical/artists/{artistId}/board/access", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M02"));
	}

	@Test
	@DisplayName("아티스트 지난 출연 작품 조회에 성공하면 200 OK와 페이지 데이터를 응답한다.")
	void 아티스트_지난출연작품_조회_성공() throws Exception {
		var pageable = PageRequest.of(0, 10);
		Page<ArtistResponse.ShowSummary> response = new PageImpl<>(
			List.of(
				ArtistResponse.ShowSummary.builder()
					.showId(21L)
					.showTitle("아티스트 지난 공연 1")
					.posterUrl("https://img.example/past-1.jpg")
					.venueName("샤롯데씨어터")
					.startTime(LocalDateTime.of(2024, 1, 5, 0, 0))
					.endTime(LocalDateTime.of(2024, 2, 18, 0, 0))
					.date("2024.01.05 - 2024.02.18")
					.build(),
				ArtistResponse.ShowSummary.builder()
					.showId(22L)
					.showTitle("아티스트 지난 공연 2")
					.posterUrl("https://img.example/past-2.jpg")
					.venueName("코엑스아티움")
					.startTime(LocalDateTime.of(2024, 5, 10, 0, 0))
					.endTime(LocalDateTime.of(2024, 6, 30, 0, 0))
					.date("2024.05.10 - 2024.06.30")
					.build()
			),
			pageable,
			2
		);

		given(artistService.getPastShows(anyLong(), any())).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}/past-shows", 1L)
				.param("page", "1")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.content[0].showId").value(21))
			.andExpect(jsonPath("$.data.content[1].showTitle").value("아티스트 지난 공연 2"))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.size").value(10))
			.andExpect(jsonPath("$.data.totalCount").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(1))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("존재하지 않는 아티스트의 지난 출연 작품을 조회하면 404를 응답한다.")
	void 아티스트_지난출연작품_조회_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST))
			.given(artistService).getPastShows(anyLong(), any());

		mockMvc.perform(get("/api/musical/artists/{artistId}/past-shows", 999L)
				.param("page", "1")
				.param("size", "10"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M02"));
	}
}

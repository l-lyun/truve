package com.truve.platform.musical.show.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.show.dto.ShowCastingResponse;
import com.truve.platform.musical.show.domain.constant.ShowScheduleStatus;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.service.ShowCastingService;
import com.truve.platform.musical.show.service.ShowDetailService;

@WebMvcTest(controllers = ShowController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class ShowControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ShowDetailService showDetailService;
	@MockitoBean
	private ShowCastingService showCastingService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("공연 상세 조회에 성공하면 200 OK와 상세 정보를 응답한다.")
	void 뮤지컬_상세_조회_성공() throws Exception {
		ShowResponse.Detail response = ShowResponse.Detail.builder()
			.showId(1L)
			.title("뮤지컬A")
			.description("설명")
			.runtimeMin(120)
			.ageLimit(8)
			.posterUrl("https://img/1.jpg")
			.noticeImgs(List.of("https://img/notice.jpg"))
			.detailImgs(List.of("https://img/detail-1.jpg", "https://img/detail-2.jpg"))
			.startTime(LocalDateTime.of(2026, 3, 1, 0, 0))
			.endTime(LocalDateTime.of(2026, 4, 1, 0, 0))
			.venue(
				ShowResponse.Venue.builder()
					.venueId(10L)
					.name("예술의전당")
					.address("서울")
					.build()
			)
			.castings(List.of(
				ShowResponse.Casting.builder()
					.showCastId(101L)
					.artistId(501L)
					.artistName("배우A")
					.profileImageUrl("https://img.example/artistA.jpg")
					.roleName("찰리")
					.order(1)
					.isLiked(false)
					.build()
			))
			.schedules(List.of(
				ShowResponse.SimpleSchedule.builder()
					.scheduleId(1L)
					.showTime(LocalDateTime.of(2026, 3, 2, 19, 30))
					.status(ShowScheduleStatus.OPEN.name())
					.build(),
				ShowResponse.SimpleSchedule.builder()
					.scheduleId(2L)
					.showTime(LocalDateTime.of(2026, 3, 3, 19, 30))
					.status(ShowScheduleStatus.CLOSED.name())
					.build(),
				ShowResponse.SimpleSchedule.builder()
					.scheduleId(3L)
					.showTime(LocalDateTime.of(2026, 3, 4, 19, 30))
					.status(ShowScheduleStatus.CANCELLED.name())
					.build()
			))
			.seatGrades(List.of(
				ShowResponse.SeatGrade.builder()
					.showSeatGradeId(1001L)
					.gradeName("VIP")
					.price(15000L)
					.colorCode("#FFD700")
					.build()
			))
			.build();

		given(showDetailService.getDetail(anyLong(), nullable(UUID.class))).willReturn(response);

		mockMvc.perform(get("/api/musical/shows/{showId}", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.showId").value(1))
			.andExpect(jsonPath("$.data.noticeImgs[0]").value("https://img/notice.jpg"))
			.andExpect(jsonPath("$.data.detailImgs[0]").value("https://img/detail-1.jpg"))
			.andExpect(jsonPath("$.data.schedules[1].status").value(ShowScheduleStatus.CLOSED.name()))
			.andExpect(jsonPath("$.data.schedules[2].status").value(ShowScheduleStatus.CANCELLED.name()))
			.andExpect(jsonPath("$.data.castings[0].artistName").value("배우A"))
			.andExpect(jsonPath("$.data.castings[0].profileImageUrl").value("https://img.example/artistA.jpg"))
			.andExpect(jsonPath("$.data.castings[0].isLiked").value(false));
	}

	@Test
	@DisplayName("noticeImgs/detailImgs가 비어도 회차/좌석 정보와 함께 200을 응답한다.")
	void 공연_상세_조회_공지없음_빈목록_성공() throws Exception {
		ShowResponse.Detail response = ShowResponse.Detail.builder()
			.showId(10L)
			.title("뮤지컬B")
			.description("설명")
			.runtimeMin(100)
			.ageLimit(12)
			.posterUrl("https://img/2.jpg")
			.noticeImgs(List.of())
			.detailImgs(List.of())
			.startTime(LocalDateTime.of(2026, 5, 1, 0, 0))
			.endTime(LocalDateTime.of(2026, 5, 31, 0, 0))
			.venue(ShowResponse.Venue.builder()
				.venueId(11L)
				.name("블루스퀘어")
				.address("서울")
				.build())
			.castings(List.of())
			.schedules(List.of())
			.seatGrades(List.of())
			.build();

		given(showDetailService.getDetail(org.mockito.ArgumentMatchers.eq(10L), nullable(UUID.class))).willReturn(response);

		mockMvc.perform(get("/api/musical/shows/{showId}", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.noticeImgs").isArray())
			.andExpect(jsonPath("$.data.noticeImgs").isEmpty())
			.andExpect(jsonPath("$.data.detailImgs").isArray())
			.andExpect(jsonPath("$.data.detailImgs").isEmpty())
			.andExpect(jsonPath("$.data.schedules").isArray())
			.andExpect(jsonPath("$.data.schedules").isEmpty())
			.andExpect(jsonPath("$.data.seatGrades").isArray())
			.andExpect(jsonPath("$.data.seatGrades").isEmpty());
	}

	@Test
	@DisplayName("존재하지 않는 공연을 조회하면 404를 응답한다.")
	void 뮤지컬_상세_조회_실패() throws Exception {
		willThrow(new CustomException(ErrorCode.NOT_FOUND_SHOW))
			.given(showDetailService).getDetail(org.mockito.ArgumentMatchers.eq(999L), nullable(UUID.class));

		mockMvc.perform(get("/api/musical/shows/{showId}", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M01"))
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	@DisplayName("캐스팅 일정 조회에 성공하면 200과 페이지 데이터를 응답한다.")
	void 캐스팅_일정_조회_성공() throws Exception {
		ShowCastingResponse.Detail response = ShowCastingResponse.Detail.builder()
			.showId(1L)
			.range(ShowCastingResponse.Range.builder()
				.from(LocalDate.of(2025, 12, 17))
				.to(LocalDate.of(2026, 3, 29))
				.build())
			.filters(ShowCastingResponse.Filters.builder()
				.artists(List.of(
					ShowCastingResponse.FilterArtist.builder()
						.artistId(1L)
						.artistName("김호영")
						.build()
				))
				.build())
			.roles(List.of(
				ShowCastingResponse.Role.builder().roleName("찰리").order(1).build(),
				ShowCastingResponse.Role.builder().roleName("롤라").order(2).build()
			))
			.page(ShowCastingResponse.Page.builder()
				.currentPage(0)
				.size(50)
				.totalElements(1)
				.totalPages(1)
				.build())
			.rows(List.of(
				ShowCastingResponse.Row.builder()
					.scheduleId(101L)
					.showTime(LocalDateTime.of(2026, 1, 2, 19, 0))
						.casts(Map.of(
							"찰리", ShowCastingResponse.CastArtist.builder()
								.artistId(1L)
								.artistName("김호영")
								.build(),
							"롤라", ShowCastingResponse.CastArtist.builder()
								.artistId(10L)
								.artistName("강홍석")
								.build()
						))
					.remainingSeats(List.of(
						ShowCastingResponse.GradeRemaining.builder()
							.gradeName("VIP")
							.remainingSeatCount(10L)
							.totalCount(20L)
							.build()
					))
					.build()
			))
			.build();

		given(showCastingService.getCastingSchedules(
			org.mockito.ArgumentMatchers.eq(1L),
			org.mockito.ArgumentMatchers.eq(LocalDate.of(2025, 12, 17)),
			org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 3, 29)),
			org.mockito.ArgumentMatchers.anyList(),
			org.mockito.ArgumentMatchers.eq(0),
			org.mockito.ArgumentMatchers.eq(50)
		)).willReturn(response);

		mockMvc.perform(get("/api/musical/shows/{showId}/casting-schedules", 1L)
				.param("from", "2025-12-17")
				.param("to", "2026-03-29")
				.param("artistIds", "1")
				.param("page", "0")
				.param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.showId").value(1))
			.andExpect(jsonPath("$.data.range.from").value("2025-12-17"))
			.andExpect(jsonPath("$.data.roles[0].roleName").value("찰리"))
				.andExpect(jsonPath("$.data.page.currentPage").value(0))
				.andExpect(jsonPath("$.data.rows[0].scheduleId").value(101))
				.andExpect(jsonPath("$.data.rows[0].casts.찰리.artistName").value("김호영"))
				.andExpect(jsonPath("$.data.rows[0].casts.찰리.profileImageUrl").doesNotExist())
				.andExpect(jsonPath("$.data.rows[0].remainingSeats[0].gradeName").value("VIP"))
				.andExpect(jsonPath("$.data.rows[0].remainingSeats[0].remainingSeatCount").value(10));
	}
}

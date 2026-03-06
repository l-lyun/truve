package com.truve.platform.musical.show.controller;

import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.show.domain.constant.ShowScheduleStatus;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.service.ShowService;

@WebMvcTest(controllers = ShowController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
class ShowControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ShowService showService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("공연 상세 조회에 성공하면 200 OK와 상세 정보를 응답한다.")
	void 뮤지컬_상세_조회_성공() throws Exception {
		// given
		ShowResponse.Detail response = ShowResponse.Detail.builder()
			.showId(1L)
			.title("뮤지컬A")
			.description("설명")
			.runtimeMin(120)
			.ageLimit(8)
			.posterUrl("https://img/1.jpg")
			.noticeUrl("https://img/notice.jpg")
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
							.build()
					,
					ShowResponse.SimpleSchedule.builder()
						.scheduleId(2L)
						.showTime(LocalDateTime.of(2026, 3, 3, 19, 30))
						.status(ShowScheduleStatus.CLOSED.name())
						.build()
					,
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

		given(showService.getDetail(anyLong())).willReturn(response);

		// when & then
		mockMvc.perform(get("/api/shows/{showId}", 1L))
			.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("ok"))
				.andExpect(jsonPath("$.data.showId").value(1))
				.andExpect(jsonPath("$.data.noticeUrl").value("https://img/notice.jpg"))
					.andExpect(jsonPath("$.data.schedules[1].status").value(ShowScheduleStatus.CLOSED.name()))
					.andExpect(jsonPath("$.data.schedules[2].status").value(ShowScheduleStatus.CANCELLED.name()))
					.andExpect(jsonPath("$.data.castings[0].artistName").value("배우A"))
					.andExpect(jsonPath("$.data.castings[0].profileImageUrl").value("https://img.example/artistA.jpg"))
					.andExpect(jsonPath("$.data.castings[0].isLiked").value(false));
	}

	@Test
	@DisplayName("noticeUrl이 없고 회차/좌석 정보가 없어도 200과 빈 목록을 응답한다.")
	void 공연_상세_조회_공지없음_빈목록_성공() throws Exception {
		ShowResponse.Detail response = ShowResponse.Detail.builder()
			.showId(10L)
			.title("뮤지컬B")
			.description("설명")
			.runtimeMin(100)
			.ageLimit(12)
			.posterUrl("https://img/2.jpg")
			.noticeUrl(null)
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

		given(showService.getDetail(10L)).willReturn(response);

		mockMvc.perform(get("/api/shows/{showId}", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.noticeUrl").value(nullValue()))
			.andExpect(jsonPath("$.data.schedules").isArray())
			.andExpect(jsonPath("$.data.schedules").isEmpty())
			.andExpect(jsonPath("$.data.seatGrades").isArray())
			.andExpect(jsonPath("$.data.seatGrades").isEmpty());
	}

	@Test
	@DisplayName("존재하지 않는 공연을 조회하면 404를 응답한다.")
	void 뮤지컬_상세_조회_실패() throws Exception {
		// given
		willThrow(new CustomException(ErrorCode.NOT_FOUND_SHOW))
			.given(showService).getDetail(999L);

		// when & then
		mockMvc.perform(get("/api/shows/{showId}", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M01"))
			.andExpect(jsonPath("$.message").exists());
	}
}

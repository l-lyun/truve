package com.truve.platform.musical.service.controller;

import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import com.truve.platform.musical.service.dto.MusicalResponse;
import com.truve.platform.musical.service.service.MusicalService;

@WebMvcTest(controllers = MusicalController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
class MusicalControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private MusicalService musicalService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("뮤지컬 상세 조회에 성공하면 200 OK와 상세 정보를 응답한다.")
	void 뮤지컬_상세_조회_성공() throws Exception {
		// given
		MusicalResponse.Detail response = MusicalResponse.Detail.builder()
			.musicalId(1L)
			.title("뮤지컬A")
			.posterUrl("https://img/1.jpg")
			.stage("예술의전당")
			.runningTime("120분")
			.ageLimit("8세 이상")
			.priceInfo("VIP 150000")
			.startDate(LocalDate.of(2026, 3, 1))
			.endDate(LocalDate.of(2026, 4, 1))
			.openAt(LocalDateTime.of(2026, 2, 20, 10, 0))
			.ratingAverage(4.6)
			.weeklyRank(3)
			.reviewCount(120)
			.timeInfo("수/금 19:30")
			.noticeUrl("https://img/notice.jpg")
			.detailsUrl("https://img/detail.jpg")
			.schedules(List.of(
				MusicalResponse.Schedule.builder()
					.scheduleId(1L)
					.dateTime(LocalDateTime.of(2026, 3, 2, 19, 30))
					.isAvailable(true)
					.actors(List.of(
						MusicalResponse.Actor.builder()
							.actorId(101L)
							.role("주연")
							.name("배우A")
							.isLiked(true)
							.build()
					))
					.build()
			))
			.seatPrices(List.of(
				MusicalResponse.SeatPrice.builder()
					.seatGrade("VIP")
					.price(150000)
					.build()
			))
			.build();

		given(musicalService.getDetail(anyLong())).willReturn(response);

		// when & then
		mockMvc.perform(get("/api/musicals/{musicalId}", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.musicalId").value(1))
			.andExpect(jsonPath("$.data.schedules[0].actors[0].name").value("배우A"));
	}

	@Test
	@DisplayName("존재하지 않는 뮤지컬을 조회하면 404를 응답한다.")
	void 뮤지컬_상세_조회_실패() throws Exception {
		// given
		willThrow(new CustomException(ErrorCode.NOT_FOUND_MUSICAL))
			.given(musicalService).getDetail(999L);

		// when & then
		mockMvc.perform(get("/api/musicals/{musicalId}", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.message").exists());
	}
}

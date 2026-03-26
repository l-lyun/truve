package org.truve.platform.ticketing.service.ticketing.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingInternalResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingInternalService;

import com.truve.platform.common.exception.ApiAdvice;

@WebMvcTest(controllers = TicketingInternalController.class)
@Import(ApiAdvice.class)
class TicketingInternalControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TicketingInternalService ticketingInternalService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@MockitoBean
	private ApplicationEventPublisher applicationEventPublisher;

	@Test
	@DisplayName("등급별 잔여 좌석 수 조회에 성공한다.")
	void 등급별_잔여좌석_조회_성공() throws Exception {
		// given
		Long showScheduleId = 1L;
		TicketingInternalResponse.RemainingSeats response = TicketingInternalResponse.RemainingSeats.of(
			showScheduleId,
			List.of(
				new TicketingInternalResponse.GradeRemaining("VIP", 10L, 20L),
				new TicketingInternalResponse.GradeRemaining("R", 30L, 50L)
			)
		);

		given(ticketingInternalService.getRemainingSeats(showScheduleId)).willReturn(response);

		// when
		ResultActions resultActions = mockMvc.perform(get("/api/ticketing/internal/{showScheduleId}/remaining", showScheduleId));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.grades[0].gradeName").value("VIP"))
			.andExpect(jsonPath("$.data.grades[0].remainingSeatCount").value(10L))
			.andExpect(jsonPath("$.data.grades[0].totalCount").value(20L))
			.andExpect(jsonPath("$.data.grades[1].gradeName").value("R"));

		verify(ticketingInternalService).getRemainingSeats(showScheduleId);
	}
}

package org.truve.platform.ticketing.service.ticketing.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingRequest;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingService;

import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import com.fasterxml.jackson.databind.ObjectMapper;


@WebMvcTest(controllers = TicketingController.class)
@Import(ApiAdvice.class)
class TicketingControllerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String ADMISSION_HEADER = "X-Admission-Token";
	private static final String SESSION_HEADER = "X-Session-Ticket";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TicketingService ticketingService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;
	@MockitoBean
	private ApplicationEventPublisher applicationEventPublisher;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("티켓팅 입장 성공")
	void 티켓팅_입장() throws Exception {
		// given
		Long showScheduleId = 1L;
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		TicketingResponse.Enter response = new TicketingResponse.Enter("session-token", 300000L);

		given(ticketingService.enter(showScheduleId, userId, "admission-token")).willReturn(response);

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/ticketing/{showScheduleId}/enter", showScheduleId)
			.header(USER_ID_HEADER, userId)
			.header(ADMISSION_HEADER, "admission-token"));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.sessionToken").value("session-token"))
			.andExpect(jsonPath("$.data.expireIn").value(300000L));
		verify(ticketingService).enter(showScheduleId, userId, "admission-token");
	}

	@Test
	@DisplayName("heartbeat 요청 성공")
	void 세션_하트비트() throws Exception {
		// when
		ResultActions resultActions = mockMvc.perform(post("/api/ticketing/{showScheduleId}/heartbeat", 1L)
			.header(USER_ID_HEADER, "11111111-1111-1111-1111-111111111111")
			.header(SESSION_HEADER, "session-token"));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.message").value("성공"));
		verify(ticketingService).heartbeat(1L, UUID.fromString("11111111-1111-1111-1111-111111111111"), "session-token");
	}

	@Test
	@DisplayName("좌석 배도 조회")
	void 좌석배치도_조회() throws Exception {
		// given
		TicketingResponse.Seats response = new TicketingResponse.Seats(List.of(
			new TicketingResponse.Section(
				1L,
				"VIP",
				"VIP",
				150000L,
				List.of(
					new TicketingResponse.Row(
						"A",
						List.of(
							new TicketingResponse.Seat(10L, 1L, SeatStatus.AVAILABLE),
							new TicketingResponse.Seat(11L, 2L, SeatStatus.SOLD)
						)
					)
				)
			)
		));

		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		given(ticketingService.getSeats(1L, userId, "session-token")).willReturn(response);

		// when
		ResultActions resultActions = mockMvc.perform(get("/api/ticketing/{showScheduleId}", 1L)
			.header(USER_ID_HEADER, userId)
			.header(SESSION_HEADER, "session-token"));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sections[0].sectionId").value(1L))
			.andExpect(jsonPath("$.data.sections[0].rows[0].row").value("A"))
			.andExpect(jsonPath("$.data.sections[0].rows[0].seats[1].status").value("SOLD"));
		verify(ticketingService).getSeats(1L, userId, "session-token");
	}

	@Test
	@DisplayName("좌석 선점에 성공")
	void 좌석_선점() throws Exception {
		// given
		TicketingRequest.HoldSeat request = new TicketingRequest.HoldSeat(List.of(10L, 11L));

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/ticketing/{showScheduleId}/hold/seat", 1L)
			.contentType(MediaType.APPLICATION_JSON)
			.header(USER_ID_HEADER, "11111111-1111-1111-1111-111111111111")
			.header(SESSION_HEADER, "session-token")
			.content(objectMapper.writeValueAsString(request)));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
		verify(ticketingService).holdSeat(1L, UUID.fromString("11111111-1111-1111-1111-111111111111"), "session-token", List.of(10L, 11L));
	}

	@Test
	@DisplayName("좌석 선점 취소에 성공")
	void 좌석_선점취소() throws Exception {
		// given
		TicketingRequest.DeleteHoldSeat request = new TicketingRequest.DeleteHoldSeat(List.of(10L, 11L));

		// when
		ResultActions resultActions = mockMvc.perform(delete("/api/ticketing/{showScheduleId}/hold/seat", 1L)
			.contentType(MediaType.APPLICATION_JSON)
			.header(USER_ID_HEADER, "11111111-1111-1111-1111-111111111111")
			.header(SESSION_HEADER, "session-token")
			.content(objectMapper.writeValueAsString(request)));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
		verify(ticketingService).cancelHoldSeat(1L, UUID.fromString("11111111-1111-1111-1111-111111111111"), "session-token", List.of(10L, 11L));
	}

	@Test
	@DisplayName("공연 회차 정보 조회에 성공")
	void 공연회차_조회() throws Exception {
		// given
		LocalDateTime startAt = LocalDateTime.of(2026, 3, 9, 20, 0);
		TicketingResponse.Show response = new TicketingResponse.Show("지킬앤하이드", "블루스퀘어", startAt);
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		given(ticketingService.getShow(userId, 1L, "session-token")).willReturn(response);

		// when
		ResultActions resultActions = mockMvc.perform(get("/api/ticketing/shows/{showScheduleId}/seats", 1L)
			.header(USER_ID_HEADER, userId)
			.header(SESSION_HEADER, "session-token"));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value("지킬앤하이드"))
			.andExpect(jsonPath("$.data.venueName").value("블루스퀘어"))
			.andExpect(jsonPath("$.data.startAt").value("2026-03-09T20:00:00"));
		verify(ticketingService).getShow(userId, 1L, "session-token");
	}

	@Test
	@DisplayName("서비스에서 예외가 발생 에러")
	void 컨트롤러_예외응답() throws Exception {
		// given
		given(ticketingService.enter(1L, UUID.fromString("11111111-1111-1111-1111-111111111111"), "bad-token"))
			.willThrow(new CustomException(ErrorCode.INVALID_ADMISSION_TOKEN));

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/ticketing/{showScheduleId}/enter", 1L)
			.header(USER_ID_HEADER, "11111111-1111-1111-1111-111111111111")
			.header(ADMISSION_HEADER, "bad-token"));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_ADMISSION_TOKEN.getMessage()))
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ADMISSION_TOKEN.getCode()));
	}

	@Test
	@DisplayName("좌석 선점 요청 바디 에러")
	void 좌석선점_요청검증실패() throws Exception {
		// given
		TicketingRequest.HoldSeat request = new TicketingRequest.HoldSeat(null);

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/ticketing/{showScheduleId}/hold/seat", 1L)
			.contentType(MediaType.APPLICATION_JSON)
			.header(USER_ID_HEADER, "11111111-1111-1111-1111-111111111111")
			.header(SESSION_HEADER, "session-token")
			.content(objectMapper.writeValueAsString(request)));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C02"));
		verify(ticketingService, never())
			.holdSeat(anyLong(), any(UUID.class), anyString(), anyList());
	}

	@Test
	@DisplayName("좌석 선점 취소 요청 바디 에러")
	void 좌석선점취소_요청검증실패() throws Exception {
		// given
		TicketingRequest.DeleteHoldSeat request = new TicketingRequest.DeleteHoldSeat(null);

		// when
		ResultActions resultActions = mockMvc.perform(delete("/api/ticketing/{showScheduleId}/hold/seat", 1L)
			.contentType(MediaType.APPLICATION_JSON)
			.header(USER_ID_HEADER, "11111111-1111-1111-1111-111111111111")
			.header(SESSION_HEADER, "session-token")
			.content(objectMapper.writeValueAsString(request)));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C02"));

		verify(ticketingService, never())
			.cancelHoldSeat(anyLong(), any(UUID.class), anyString(), anyList());
	}
}

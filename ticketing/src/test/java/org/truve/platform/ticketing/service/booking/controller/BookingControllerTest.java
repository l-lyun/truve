package org.truve.platform.ticketing.service.booking.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.dto.BookingResponse;
import org.truve.platform.ticketing.service.booking.service.BookingService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.ApiAdvice;

@WebMvcTest(controllers = BookingController.class)
@Import(ApiAdvice.class)
class BookingControllerTest {
	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookingService bookingService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;
	@MockitoBean
	private ApplicationEventPublisher applicationEventPublisher;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void 예매생성요청에_사용자_세션_회차_좌석을_전달한다() throws Exception {
		BookingRequest.Create request = new BookingRequest.Create(100L, List.of(10L, 11L));
		given(bookingService.create(eq(USER_ID), eq("session-token"), any(BookingRequest.Create.class)))
			.willReturn(new BookingResponse.Create("R-001"));

		mockMvc.perform(post("/api/bookings")
				.header("X-User-Id", USER_ID)
				.header("X-Session-Ticket", "session-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reservationNumber").value("R-001"));

		verify(bookingService).create(eq(USER_ID), eq("session-token"), argThat(actual ->
			actual.getShowScheduleId().equals(100L)
				&& actual.getScheduledSeatIds().equals(List.of(10L, 11L))));
	}

	@Test
	void 세션헤더가_없으면_400을_반환한다() throws Exception {
		BookingRequest.Create request = new BookingRequest.Create(100L, List.of(10L));

		mockMvc.perform(post("/api/bookings")
				.header("X-User-Id", USER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 좌석을_5개_요청하면_400을_반환한다() throws Exception {
		BookingRequest.Create request = new BookingRequest.Create(100L, List.of(1L, 2L, 3L, 4L, 5L));

		mockMvc.perform(post("/api/bookings")
				.header("X-User-Id", USER_ID)
				.header("X-Session-Ticket", "session-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 좌석_ID에_null이_포함되면_400을_반환한다() throws Exception {
		String request = """
			{"showScheduleId":100,"scheduledSeatIds":[null]}
			""";

		mockMvc.perform(post("/api/bookings")
				.header("X-User-Id", USER_ID)
				.header("X-Session-Ticket", "session-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest());
	}
}

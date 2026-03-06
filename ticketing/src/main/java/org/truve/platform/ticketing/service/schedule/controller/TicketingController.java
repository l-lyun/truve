package org.truve.platform.ticketing.service.schedule.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.ticketing.service.schedule.dto.TicketingResponse;
import org.truve.platform.ticketing.service.schedule.service.TicketingService;

import com.truve.platform.common.response.ApiResult;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tickets")
public class TicketingController {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String ADMISSION_HEADER = "X-Admission-Token";
	private static final String SESSION_HEADER = "X-Session-Ticket";

	private final TicketingService ticketingService;

	@PostMapping("/{showScheduleId}/enter")
	public ApiResult<TicketingResponse.Enter> enter(
		@PathVariable Long showScheduleId,
		@RequestHeader(value = USER_ID_HEADER) Long userId,
		@RequestHeader(value = ADMISSION_HEADER, required = false) String admissionToken
	) {
		var response = ticketingService.enter(showScheduleId, userId, admissionToken);
		return ApiResult.ok(response);
	}

	@PostMapping("/{showScheduleId}/heartbeat")
	public ApiResult<Void> heartbeat(
		@PathVariable Long showScheduleId,
		@RequestHeader(value = USER_ID_HEADER) Long userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken
	) {
		ticketingService.heartbeat(showScheduleId, userId, sessionToken);
		return ApiResult.ok();
	}

	@PostMapping("/{showScheduleId}/hold/seat/{seatId}")
	public ApiResult<Void> holdSeat(
		@PathVariable Long seatId,
		@PathVariable Long showScheduleId,
		@RequestHeader(value = USER_ID_HEADER) Long userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken
	) {
		ticketingService.holdSeat(showScheduleId, userId, sessionToken, seatId);
		return ApiResult.ok();
	}

}

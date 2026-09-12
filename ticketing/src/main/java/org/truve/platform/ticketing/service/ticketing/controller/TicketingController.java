package org.truve.platform.ticketing.service.ticketing.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingRequest;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingService;
import org.truve.platform.ticketing.service.ticketing.service.SeatHoldSagaService;

import java.util.UUID;

import com.truve.platform.common.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticketing")
@Tag(name = "Ticketing", description = "티켓팅 좌석 조회/점유 API")
public class TicketingController {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String ADMISSION_HEADER = "X-Admission-Token";
	private static final String SESSION_HEADER = "X-Session-Ticket";
	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final TicketingService ticketingService;
	private final SeatHoldSagaService seatHoldSagaService;

	@Operation(
		summary = "티켓팅 입장",
		description = "대기열 통과 후 티켓팅 세션에 입장합니다. 프론트는 Authorization Bearer Token과 X-Admission-Token을 함께 전달하면 됩니다.",
		parameters = {
			@Parameter(
				name = ADMISSION_HEADER,
				description = "대기열 통과 후 발급된 입장 토큰",
				required = true
			)
		}
	)
	@PostMapping("/{showScheduleId}/enter")
	public ApiResult<TicketingResponse.Enter> enter(
		@PathVariable Long showScheduleId,
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = ADMISSION_HEADER, required = false) String admissionToken
	) {
		var response = ticketingService.enter(showScheduleId, userId, admissionToken);
		return ApiResult.ok(response);
	}

	@Operation(
		summary = "티켓팅 세션 heartbeat",
		description = "티켓팅 세션을 연장합니다. 프론트는 Authorization Token과 X-Session-Ticket을 전달하면 됩니다.",
		parameters = {
			@Parameter(
				name = SESSION_HEADER,
				description = "티켓팅 입장 후 발급된 세션 토큰",
				required = true
			)
		}
	)
	@PostMapping("/{showScheduleId}/heartbeat")
	public ApiResult<Void> heartbeat(
		@PathVariable Long showScheduleId,
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken
	) {
		ticketingService.heartbeat(showScheduleId, userId, sessionToken);
		return ApiResult.ok();
	}

	@Operation(
		summary = "좌석 배치도 조회",
		description = "티켓팅 세션 검증 후 공연 회차의 좌석 배치도와 상태를 조회합니다.",
		parameters = {
			@Parameter(
				name = SESSION_HEADER,
				description = "티켓팅 입장 후 발급된 세션 토큰",
				required = true
			)
		}
	)
	@GetMapping("/{showScheduleId}")
	public ApiResult<TicketingResponse.Seats> getSeats(
		@PathVariable Long showScheduleId,
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken
	) {
		var response = ticketingService.getSeats(showScheduleId, userId, sessionToken);
		return ApiResult.ok(response);
	}

	@Operation(
		summary = "좌석 선점",
		description = "선택한 좌석을 임시 선점합니다. 최대 4좌석까지 가능합니다.",
		parameters = {
			@Parameter(
				name = SESSION_HEADER,
				description = "티켓팅 입장 후 발급된 세션 토큰",
				required = true
			),
			@Parameter(
				name = IDEMPOTENCY_HEADER,
				description = "같은 좌석 선점 요청을 재시도할 때 재사용하는 멱등 키",
				required = true
			)
		}
	)
	@PostMapping("/{showScheduleId}/hold/seat")
	public ApiResult<TicketingResponse.HoldAccepted> holdSeat(
		@PathVariable Long showScheduleId,
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken,
		@RequestHeader(value = IDEMPOTENCY_HEADER) String idempotencyKey,
		@RequestBody @Valid TicketingRequest.HoldSeat request
	) {
		var response = seatHoldSagaService.hold(
			showScheduleId, userId, sessionToken, idempotencyKey, request.getScheduledSeatIds());
		return ApiResult.ok(response);
	}

	@Operation(
		summary = "공연 회차 정보 조회",
		description = "티켓팅 화면 상단에 필요한 공연 기본 정보를 조회합니다.",
		parameters = {
			@Parameter(
				name = SESSION_HEADER,
				description = "티켓팅 입장 후 발급된 세션 토큰",
				required = true
			)
		}
	)
	@GetMapping("/shows/{showScheduleId}/seats")
	public ApiResult<TicketingResponse.Show> getShow(
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken,
		@PathVariable Long showScheduleId
	) {
		var response = ticketingService.getShow(userId, showScheduleId, sessionToken);
		return ApiResult.ok(response);
	}


	@Operation(
		summary = "점유 좌석 취소",
		description = "HOLD 했던 좌석을 반납합니다.",
		parameters = {
			@Parameter(
				name = SESSION_HEADER,
				description = "티켓팅 입장 후 발급된 세션 토큰",
				required = true
			)
		}
	)
	@DeleteMapping("/{showScheduleId}/hold/seat")
	public ApiResult<Void> deleteHoldSeat(
		@PathVariable Long showScheduleId,
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken,
		@RequestBody @Valid TicketingRequest.DeleteHoldSeat request
	) {
		ticketingService.cancelHoldSeat(showScheduleId, userId, sessionToken, request.getScheduledSeatIds());
		return ApiResult.ok();
	}

	@PostMapping("/{showScheduledId}/exit")
	public ApiResult<Void> exitTicketing(
		@PathVariable Long showScheduledId,
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@RequestHeader(value = SESSION_HEADER) String sessionToken
	) {
		ticketingService.exitTicketing(showScheduledId, userId, sessionToken);
		return ApiResult.ok();
	}

}

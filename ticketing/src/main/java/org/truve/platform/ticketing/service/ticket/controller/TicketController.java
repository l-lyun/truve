package org.truve.platform.ticketing.service.ticket.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.ticketing.service.ticket.dto.TicketRequest;
import org.truve.platform.ticketing.service.ticket.dto.TicketResponse;
import org.truve.platform.ticketing.service.ticket.service.TicketService;

import com.truve.platform.common.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tickets")
public class TicketController {
	private static final String USER_ID_HEADER = "X-User-Id";

	private final TicketService ticketService;

	@Operation(summary = "예매 내역 생성", description = "예매 및 티켓 정보를 저장합니다.")
	@PostMapping
	public ApiResult<TicketResponse.Create> create(
		@RequestHeader(USER_ID_HEADER) Long userId,
		@RequestBody @Valid TicketRequest.Create request) {
		return ApiResult.ok(ticketService.create(userId, request));
	}
}

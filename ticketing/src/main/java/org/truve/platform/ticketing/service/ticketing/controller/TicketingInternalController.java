package org.truve.platform.ticketing.service.ticketing.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingInternalResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingInternalService;

import com.truve.platform.common.response.ApiResult;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticketing/internal")
@Tag(name = "Internal Ticketing", description = "백엔드 내부 통신 용 API")

// TODO: 내부 서비스 간 통신 시 보안 방식 검토
public class TicketingInternalController {
	private final TicketingInternalService ticketingInternalService;

	@GetMapping("/{showScheduleId}/remaining")
	public ApiResult<TicketingInternalResponse.RemainingSeats> getRemainingSeats(
		@PathVariable Long showScheduleId
	) {
		return ApiResult.ok(ticketingInternalService.getRemainingSeats(showScheduleId));
	}
}

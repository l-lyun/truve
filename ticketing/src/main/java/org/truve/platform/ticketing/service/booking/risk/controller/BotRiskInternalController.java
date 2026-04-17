package org.truve.platform.ticketing.service.booking.risk.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.ticketing.service.booking.risk.dto.BeBotRiskReportRequest;
import org.truve.platform.ticketing.service.booking.risk.service.BookingBotRiskService;

import com.truve.platform.common.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings/internal/bot-risk")
public class BotRiskInternalController {

	private final BookingBotRiskService bookingBotRiskService;

	@Operation(summary = "BE 봇 탐지 결과 반영", description = "결제 이후 판정된 BE 봇 탐지 결과를 사용자 요약 정보에 반영합니다.")
	@PostMapping("/be")
	public ApiResult<Void> reportBeRisk(@RequestBody BeBotRiskReportRequest request) {
		bookingBotRiskService.reportBeRisk(request);
		return ApiResult.ok();
	}
}

package org.truve.platform.queue.service.queue.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truve.platform.queue.service.common.response.ApiResult;
import org.truve.platform.queue.service.queue.dto.QueueResponse;
import org.truve.platform.queue.service.queue.service.QueueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor

@Tag(name = "Queue", description = "티켓팅 대기열 API")
public class QueueController {

	private static final String USER_ID_HEADER = "X-User-Id";

	private final QueueService queueService;

	@Operation(summary = "대기열 진입", description = "사용자를 공연 회차별 대기열에 등록합니다.")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "대기열 진입 성공"
		)
	})
	@PostMapping("/{showId}/enter")
	public ApiResult<Void> enter(
		@PathVariable String showId,
		@RequestHeader(value = USER_ID_HEADER, required = false) String userId
	) {
		queueService.enter(showId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "대기열 상태 조회", description = "대기열 순번 또는 READY 토큰 상태를 조회합니다.")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "대기열 상태 조회 성공",
			content = @Content(schema = @Schema(implementation =  QueueResponse.Status.class))
		)
	})
	@GetMapping("/{showId}/status")
	public ApiResult<QueueResponse.Status> status(
		@PathVariable String showId,
		@RequestHeader(value = USER_ID_HEADER) String userId
	) {
		var response = queueService.status(showId, userId);
		return ApiResult.ok(response);
	}

	@DeleteMapping("/{showId}/cancel")
	public ApiResult<Void> cancel(
		@PathVariable String showId,
		@RequestHeader(value = USER_ID_HEADER, required = false) String userId
	) {
		queueService.cancel(showId, userId);
		return ApiResult.ok();
	}
}

package com.truve.platform.musical.review.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.review.dto.ReviewRequest;
import com.truve.platform.musical.review.service.ReviewService;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musical/review")
public class ReviewController {

	private static final String USER_ID_HEADER = "X-User-Id";

	private final ReviewService reviewService;

	@PostMapping("/{showId}")
	public ApiResult<Void> create(
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@PathVariable	Long showId,
		@RequestBody @Valid ReviewRequest.Create request
	) {
		reviewService.create(userId, showId, request);
		return ApiResult.ok();
	}

}
